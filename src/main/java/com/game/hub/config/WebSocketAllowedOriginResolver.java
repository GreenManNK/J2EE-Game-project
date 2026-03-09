package com.game.hub.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class WebSocketAllowedOriginResolver {
    private final String configuredPatterns;
    private final String publicBaseUrl;
    private final String publicGameUrl;
    private final String additionalPatterns;

    public WebSocketAllowedOriginResolver(
        @Value("${app.websocket.allowed-origin-patterns:*}") String configuredPatterns,
        @Value("${PUBLIC_BASE_URL:}") String publicBaseUrl,
        @Value("${PUBLIC_GAME_URL:}") String publicGameUrl,
        @Value("${app.websocket.additional-origin-patterns:}") String additionalPatterns
    ) {
        this.configuredPatterns = configuredPatterns;
        this.publicBaseUrl = publicBaseUrl;
        this.publicGameUrl = publicGameUrl;
        this.additionalPatterns = additionalPatterns;
    }

    public String[] resolve() {
        Set<String> resolved = new LinkedHashSet<>();
        addCsvValues(resolved, configuredPatterns);
        addOriginFromUrl(resolved, publicBaseUrl);
        addOriginFromUrl(resolved, publicGameUrl);
        addCsvValues(resolved, additionalPatterns);
        if (resolved.isEmpty()) {
            resolved.add("*");
        }
        return resolved.toArray(String[]::new);
    }

    private void addCsvValues(Set<String> target, String csv) {
        if (csv == null) {
            return;
        }
        for (String raw : csv.split(",")) {
            String value = raw == null ? "" : raw.trim();
            if (!value.isEmpty()) {
                target.add(value);
            }
        }
    }

    private void addOriginFromUrl(Set<String> target, String rawUrl) {
        String normalized = normalizeOrigin(rawUrl);
        if (!normalized.isEmpty()) {
            target.add(normalized);
        }
    }

    static String normalizeOrigin(String rawUrl) {
        if (rawUrl == null) {
            return "";
        }
        String value = rawUrl.trim();
        if (value.isEmpty()) {
            return "";
        }
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || scheme.isBlank() || host == null || host.isBlank()) {
                return "";
            }
            int port = uri.getPort();
            boolean defaultPort = ("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443);
            return defaultPort || port < 0
                ? (scheme + "://" + host)
                : (scheme + "://" + host + ":" + port);
        } catch (Exception ignored) {
            return "";
        }
    }
}
