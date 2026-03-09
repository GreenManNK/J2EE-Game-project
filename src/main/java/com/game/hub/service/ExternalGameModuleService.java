package com.game.hub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class ExternalGameModuleService {
    private static final Set<String> REQUEST_HEADERS_TO_SKIP = Set.of(
        "connection",
        "content-length",
        "host",
        "upgrade",
        "transfer-encoding"
    );
    private static final Set<String> RESPONSE_HEADERS_TO_SKIP = Set.of(
        "connection",
        "content-length",
        "transfer-encoding"
    );

    private final ObjectMapper objectMapper;
    private final Path registryPath;
    private final Duration importTimeout;
    private final Duration proxyTimeout;
    private final HttpClient httpClient;
    private final Object fileLock = new Object();

    @Autowired
    public ExternalGameModuleService(
        ObjectMapper objectMapper,
        @Value("${app.game-modules.external-registry-path:config/external-game-modules.json}") String registryPath,
        @Value("${app.game-modules.import-timeout-seconds:10}") int importTimeoutSeconds,
        @Value("${app.game-modules.proxy-timeout-seconds:20}") int proxyTimeoutSeconds
    ) {
        this(
            objectMapper,
            Paths.get(registryPath),
            Duration.ofSeconds(Math.max(2, importTimeoutSeconds)),
            Duration.ofSeconds(Math.max(2, proxyTimeoutSeconds)),
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(2, importTimeoutSeconds)))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()
        );
    }

    public ExternalGameModuleService(ObjectMapper objectMapper,
                                     Path registryPath,
                                     Duration importTimeout,
                                     Duration proxyTimeout,
                                     HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.registryPath = registryPath;
        this.importTimeout = importTimeout;
        this.proxyTimeout = proxyTimeout;
        this.httpClient = httpClient;
    }

    public List<GameCatalogItem> listCatalogItems() {
        synchronized (fileLock) {
            return readRegistry().modules().stream()
                .map(this::safeNormalize)
                .flatMap(Optional::stream)
                .toList();
        }
    }

    public List<ExternalGameModuleConfig> listConfigurations() {
        synchronized (fileLock) {
            return List.copyOf(readRegistry().modules());
        }
    }

    public byte[] exportRegistryJson() {
        synchronized (fileLock) {
            ExternalModuleRegistry registry = readRegistry();
            try {
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(registry);
            } catch (IOException ex) {
                throw new IllegalStateException("Khong xuat duoc registry external game module.", ex);
            }
        }
    }

    public Optional<GameCatalogItem> findByCode(String code) {
        String normalizedCode = normalizeCode(code);
        if (normalizedCode.isBlank()) {
            return Optional.empty();
        }
        return listCatalogItems().stream()
            .filter(item -> normalizedCode.equals(normalizeCode(item.code())))
            .findFirst();
    }

    public GameModuleImportResult upsertModules(List<ExternalGameModuleConfig> modules, boolean replaceAll) {
        if (modules == null || modules.isEmpty()) {
            throw new IllegalArgumentException("Danh sach module khong duoc de trong.");
        }

        synchronized (fileLock) {
            List<ExternalGameModuleConfig> normalizedIncoming = modules.stream()
                .filter(Objects::nonNull)
                .map(module -> normalizeConfig(module, module.manifestUrl()))
                .toList();

            LinkedHashMap<String, ExternalGameModuleConfig> merged = new LinkedHashMap<>();
            if (!replaceAll) {
                readRegistry().modules().forEach(existing -> merged.put(normalizeCode(existing.code()), existing));
            }
            normalizedIncoming.forEach(module -> merged.put(normalizeCode(module.code()), module));

            ExternalModuleRegistry registry = new ExternalModuleRegistry(1, new ArrayList<>(merged.values()));
            writeRegistry(registry);
            List<GameCatalogItem> savedItems = registry.modules().stream()
                .map(this::normalizeToCatalogItem)
                .toList();
            return new GameModuleImportResult(savedItems.size(), normalizedIncoming.size(), savedItems);
        }
    }

    public GameModulePreviewResult previewModules(List<ExternalGameModuleConfig> modules) {
        if (modules == null || modules.isEmpty()) {
            throw new IllegalArgumentException("Danh sach module khong duoc de trong.");
        }

        List<GameCatalogItem> previewItems = modules.stream()
            .filter(Objects::nonNull)
            .map(module -> normalizeConfig(module, module.manifestUrl()))
            .map(this::normalizeToCatalogItem)
            .toList();
        return new GameModulePreviewResult(previewItems.size(), previewItems, "");
    }

    public GameModulePreviewResult previewManifestUrl(String manifestUrl) {
        String normalizedManifestUrl = normalizeUrl(manifestUrl);
        if (normalizedManifestUrl.isBlank()) {
            throw new IllegalArgumentException("Manifest URL khong hop le.");
        }

        List<ExternalGameModuleConfig> modules = fetchManifestModules(normalizedManifestUrl);
        List<GameCatalogItem> previewItems = modules.stream()
            .map(this::normalizeToCatalogItem)
            .toList();
        return new GameModulePreviewResult(previewItems.size(), previewItems, normalizedManifestUrl);
    }

    public GameModuleImportResult importFromManifestUrl(String manifestUrl, boolean replaceAll) {
        String normalizedManifestUrl = normalizeUrl(manifestUrl);
        if (normalizedManifestUrl.isBlank()) {
            throw new IllegalArgumentException("Manifest URL khong hop le.");
        }

        List<ExternalGameModuleConfig> modules = fetchManifestModules(normalizedManifestUrl);
        return upsertModules(modules, replaceAll);
    }

    private List<ExternalGameModuleConfig> fetchManifestModules(String normalizedManifestUrl) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(normalizedManifestUrl))
            .GET()
            .timeout(importTimeout)
            .header("Accept", "application/json")
            .build();

        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalArgumentException("Khong tai duoc manifest: HTTP " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            return extractModules(root, normalizedManifestUrl);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Tai manifest bi ngat giua chung.", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("Khong doc duoc manifest JSON.", ex);
        }
    }

    public boolean deleteModule(String code) {
        String normalizedCode = normalizeCode(code);
        if (normalizedCode.isBlank()) {
            return false;
        }

        synchronized (fileLock) {
            LinkedHashMap<String, ExternalGameModuleConfig> kept = new LinkedHashMap<>();
            readRegistry().modules().forEach(existing -> {
                String existingCode = normalizeCode(existing.code());
                if (!normalizedCode.equals(existingCode)) {
                    kept.put(existingCode, existing);
                }
            });

            boolean changed = kept.size() != readRegistry().modules().size();
            if (changed) {
                writeRegistry(new ExternalModuleRegistry(1, new ArrayList<>(kept.values())));
            }
            return changed;
        }
    }

    public ProxyResponse proxyApiRequest(String code,
                                         String remainderPath,
                                         String queryString,
                                         String method,
                                         HttpHeaders headers,
                                         byte[] body) {
        GameCatalogItem module = findByCode(code)
            .orElseThrow(() -> new IllegalArgumentException("Khong tim thay module game."));
        if (!module.hasExternalApi()) {
            throw new IllegalArgumentException("Module nay khong co apiBaseUrl.");
        }

        String targetUrl = buildProxyTargetUrl(module.apiBaseUrl(), remainderPath, queryString);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(targetUrl))
            .timeout(proxyTimeout);

        headers.forEach((name, values) -> {
            if (shouldForwardRequestHeader(name)) {
                values.forEach(value -> builder.header(name, value));
            }
        });

        byte[] safeBody = body == null ? new byte[0] : body;
        HttpRequest.BodyPublisher bodyPublisher = safeBody.length == 0
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofByteArray(safeBody);
        builder.method(normalizeMethod(method), bodyPublisher);

        try {
            HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            HttpHeaders responseHeaders = new HttpHeaders();
            response.headers().map().forEach((name, values) -> {
                if (shouldForwardResponseHeader(name)) {
                    responseHeaders.put(name, new ArrayList<>(values));
                }
            });
            return new ProxyResponse(response.statusCode(), responseHeaders, response.body(), targetUrl);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Proxy api bi ngat giua chung.", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("Khong goi duoc external game api.", ex);
        }
    }

    private Optional<GameCatalogItem> safeNormalize(ExternalGameModuleConfig config) {
        try {
            return Optional.of(normalizeToCatalogItem(config));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private ExternalModuleRegistry readRegistry() {
        if (!Files.exists(registryPath)) {
            return new ExternalModuleRegistry(1, List.of());
        }

        try {
            byte[] raw = Files.readAllBytes(registryPath);
            if (raw.length == 0) {
                return new ExternalModuleRegistry(1, List.of());
            }
            JsonNode root = objectMapper.readTree(raw);
            List<ExternalGameModuleConfig> modules = extractModules(root, "");
            return new ExternalModuleRegistry(1, modules);
        } catch (IOException ex) {
            throw new IllegalStateException("Khong doc duoc registry external game module.", ex);
        }
    }

    private void writeRegistry(ExternalModuleRegistry registry) {
        try {
            Path parent = registryPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(registryPath.toFile(), registry);
        } catch (IOException ex) {
            throw new IllegalStateException("Khong ghi duoc registry external game module.", ex);
        }
    }

    private List<ExternalGameModuleConfig> extractModules(JsonNode root, String manifestUrl) throws IOException {
        if (root == null || root.isNull()) {
            return List.of();
        }
        if (root.isArray()) {
            return extractArray(root, manifestUrl);
        }
        if (root.isObject() && root.has("modules") && root.get("modules").isArray()) {
            return extractArray(root.get("modules"), manifestUrl);
        }
        if (root.isObject()) {
            return List.of(normalizeConfig(objectMapper.treeToValue(root, ExternalGameModuleConfig.class), manifestUrl));
        }
        throw new IllegalArgumentException("Manifest phai la object hoac array.");
    }

    private List<ExternalGameModuleConfig> extractArray(JsonNode arrayNode, String manifestUrl) throws IOException {
        List<ExternalGameModuleConfig> modules = new ArrayList<>();
        for (JsonNode node : arrayNode) {
            modules.add(normalizeConfig(objectMapper.treeToValue(node, ExternalGameModuleConfig.class), manifestUrl));
        }
        return modules;
    }

    private ExternalGameModuleConfig normalizeConfig(ExternalGameModuleConfig config, String manifestUrlOverride) {
        if (config == null) {
            throw new IllegalArgumentException("Module config khong hop le.");
        }

        String code = normalizeCode(config.code());
        if (code.isBlank()) {
            throw new IllegalArgumentException("Module code la bat buoc.");
        }

        String displayName = normalizeText(config.displayName(), code.toUpperCase(Locale.ROOT));
        String shortLabel = normalizeText(config.shortLabel(), displayName);
        String description = normalizeText(
            config.description(),
            "Module game ngoai duoc tich hop vao hub."
        );
        String iconClass = normalizeText(config.iconClass(), "bi-controller");
        String embedUrl = normalizeUrl(config.embedUrl());
        String apiBaseUrl = normalizeUrl(config.apiBaseUrl());
        String sourceType = normalizeSourceType(config.sourceType(), apiBaseUrl, embedUrl, config.primaryActionUrl());
        String detailMode = normalizeDetailMode(config.detailMode(), embedUrl, apiBaseUrl, config.primaryActionUrl());
        String primaryActionUrl = normalizePrimaryActionUrl(config.primaryActionUrl(), embedUrl);
        String primaryActionLabel = normalizePrimaryActionLabel(config.primaryActionLabel(), primaryActionUrl, apiBaseUrl);
        String runtime = normalizeText(config.runtime(), "external");
        String manifestUrl = normalizeText(config.manifestUrl(), normalizeUrl(manifestUrlOverride));
        boolean availableNow = config.availableNow() == null || config.availableNow();
        boolean supportsGuest = config.supportsGuest() == null || config.supportsGuest();
        boolean supportsOnline = config.supportsOnline() != null && config.supportsOnline();
        boolean supportsOffline = config.supportsOffline() != null && config.supportsOffline();
        boolean overrideExisting = config.overrideExisting() != null && config.overrideExisting();
        List<String> roadmapItems = sanitizeRoadmap(config.roadmapItems(), sourceType, runtime, apiBaseUrl);

        return new ExternalGameModuleConfig(
            code,
            displayName,
            shortLabel,
            description,
            iconClass,
            availableNow,
            supportsOnline,
            supportsOffline,
            supportsGuest,
            primaryActionLabel,
            primaryActionUrl,
            roadmapItems,
            sourceType,
            runtime,
            detailMode,
            embedUrl,
            apiBaseUrl,
            manifestUrl,
            overrideExisting
        );
    }

    private GameCatalogItem normalizeToCatalogItem(ExternalGameModuleConfig config) {
        ExternalGameModuleConfig normalized = normalizeConfig(config, config == null ? "" : config.manifestUrl());
        return new GameCatalogItem(
            normalized.code(),
            normalized.displayName(),
            normalized.shortLabel(),
            normalized.description(),
            normalized.iconClass(),
            Boolean.TRUE.equals(normalized.availableNow()),
            Boolean.TRUE.equals(normalized.supportsOnline()),
            Boolean.TRUE.equals(normalized.supportsOffline()),
            Boolean.TRUE.equals(normalized.supportsGuest()),
            normalized.primaryActionLabel(),
            normalized.primaryActionUrl(),
            normalized.roadmapItems(),
            normalized.sourceType(),
            normalized.runtime(),
            normalized.detailMode(),
            normalized.embedUrl(),
            normalized.apiBaseUrl(),
            normalized.manifestUrl(),
            Boolean.TRUE.equals(normalized.overrideExisting())
        );
    }

    private String buildProxyTargetUrl(String apiBaseUrl, String remainderPath, String queryString) {
        String base = trimTrailingSlash(apiBaseUrl);
        String remainder = remainderPath == null ? "" : remainderPath.trim();
        if (!remainder.startsWith("/")) {
            remainder = "/" + remainder;
        }
        StringBuilder url = new StringBuilder(base).append(remainder);
        if (queryString != null && !queryString.isBlank()) {
            url.append('?').append(queryString);
        }
        return url.toString();
    }

    private String normalizeMethod(String method) {
        if (method == null || method.isBlank()) {
            return "GET";
        }
        return method.trim().toUpperCase(Locale.ROOT);
    }

    private boolean shouldForwardRequestHeader(String name) {
        return name != null && !REQUEST_HEADERS_TO_SKIP.contains(name.trim().toLowerCase(Locale.ROOT));
    }

    private boolean shouldForwardResponseHeader(String name) {
        return name != null && !RESPONSE_HEADERS_TO_SKIP.contains(name.trim().toLowerCase(Locale.ROOT));
    }

    private String normalizePrimaryActionUrl(String primaryActionUrl, String embedUrl) {
        String normalizedPrimaryAction = normalizeText(primaryActionUrl, "");
        if (!normalizedPrimaryAction.isBlank()) {
            return normalizedPrimaryAction;
        }
        return embedUrl;
    }

    private String normalizePrimaryActionLabel(String primaryActionLabel, String primaryActionUrl, String apiBaseUrl) {
        String normalized = normalizeText(primaryActionLabel, "");
        if (!normalized.isBlank()) {
            return normalized;
        }
        if (!normalizeText(primaryActionUrl, "").isBlank()) {
            return "Mo module";
        }
        if (!normalizeText(apiBaseUrl, "").isBlank()) {
            return "Mo gateway API";
        }
        return "";
    }

    private String normalizeDetailMode(String detailMode, String embedUrl, String apiBaseUrl, String primaryActionUrl) {
        String normalized = normalizeText(detailMode, "").toLowerCase(Locale.ROOT);
        if ("embed".equals(normalized) || "redirect".equals(normalized) || "api".equals(normalized)) {
            return normalized;
        }
        if (!embedUrl.isBlank()) {
            return "embed";
        }
        if (!normalizeText(primaryActionUrl, "").isBlank()) {
            return "redirect";
        }
        if (!apiBaseUrl.isBlank()) {
            return "api";
        }
        return "redirect";
    }

    private String normalizeSourceType(String sourceType, String apiBaseUrl, String embedUrl, String primaryActionUrl) {
        String normalized = normalizeText(sourceType, "").toLowerCase(Locale.ROOT);
        if ("external-module".equals(normalized) || "external-api".equals(normalized)) {
            return normalized;
        }
        if (!apiBaseUrl.isBlank() && embedUrl.isBlank() && normalizeText(primaryActionUrl, "").isBlank()) {
            return "external-api";
        }
        return "external-module";
    }

    private List<String> sanitizeRoadmap(List<String> roadmapItems, String sourceType, String runtime, String apiBaseUrl) {
        List<String> items = new ArrayList<>();
        if (roadmapItems != null) {
            roadmapItems.stream()
                .map(item -> normalizeText(item, ""))
                .filter(item -> !item.isBlank())
                .forEach(items::add);
        }
        if (items.isEmpty()) {
            items.add("Nguon module: " + sourceType);
            items.add("Runtime/module: " + runtime);
            if (!normalizeText(apiBaseUrl, "").isBlank()) {
                items.add("Gateway API cung domain: se goi qua " + apiBaseUrl);
            }
        }
        return List.copyOf(items);
    }

    private String normalizeCode(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9-]{1,63}")) {
            return "";
        }
        return normalized;
    }

    private String normalizeText(String value, String fallback) {
        if (value == null) {
            return fallback == null ? "" : fallback;
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            return fallback == null ? "" : fallback;
        }
        return normalized;
    }

    private String normalizeUrl(String value) {
        String normalized = normalizeText(value, "");
        if (normalized.isBlank()) {
            return "";
        }
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return trimTrailingSlash(normalized);
        }
        return normalized;
    }

    private String trimTrailingSlash(String value) {
        String normalized = value == null ? "" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private record ExternalModuleRegistry(int version, List<ExternalGameModuleConfig> modules) {
    }

    public record GameModuleImportResult(int totalConfiguredModules,
                                         int importedModules,
                                         List<GameCatalogItem> savedItems) {
    }

    public record GameModulePreviewResult(int previewedModules,
                                          List<GameCatalogItem> modules,
                                          String manifestUrl) {
    }

    public record ProxyResponse(int statusCode,
                                HttpHeaders headers,
                                byte[] body,
                                String targetUrl) {
    }
}
