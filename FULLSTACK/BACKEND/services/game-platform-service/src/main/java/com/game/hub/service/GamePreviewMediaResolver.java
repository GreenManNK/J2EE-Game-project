package com.game.hub.service;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;

@Component
public class GamePreviewMediaResolver {
    private static final String CATALOG_MEDIA_PATTERN = "classpath*:/static/images/games/home/%s.*";

    private final ResourcePatternResolver resourcePatternResolver;

    public GamePreviewMediaResolver() {
        this(new PathMatchingResourcePatternResolver());
    }

    public GamePreviewMediaResolver(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    public Optional<ResolvedPreviewMedia> resolveCatalogMedia(String code) {
        String normalizedCode = normalizeCode(code);
        if (normalizedCode.isBlank()) {
            return Optional.empty();
        }
        try {
            return Arrays.stream(resourcePatternResolver.getResources(patternFor(normalizedCode)))
                .filter(Resource::exists)
                .map(this::toResolvedPreviewMedia)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparingInt(ResolvedPreviewMedia::priority).thenComparing(ResolvedPreviewMedia::url))
                .findFirst();
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    public String resolveMediaKind(String mediaUrl, String configuredKind) {
        if (mediaUrl == null || mediaUrl.isBlank()) {
            return "";
        }
        String normalizedKind = normalizeMediaKind(configuredKind);
        if ("video".equals(normalizedKind) || "image".equals(normalizedKind)) {
            return normalizedKind;
        }
        return inferMediaKind(mediaUrl);
    }

    private Optional<ResolvedPreviewMedia> toResolvedPreviewMedia(Resource resource) {
        String filename = resource.getFilename();
        if (filename == null || filename.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new ResolvedPreviewMedia(
            "/images/games/home/" + UriUtils.encodePathSegment(filename, StandardCharsets.UTF_8),
            inferMediaKind(filename)
        ));
    }

    private String inferMediaKind(String mediaUrl) {
        Optional<MediaType> mediaType = MediaTypeFactory.getMediaType(mediaUrl);
        if (mediaType.isPresent()) {
            String topLevelType = mediaType.get().getType();
            if ("video".equalsIgnoreCase(topLevelType)) {
                return "video";
            }
            if ("image".equalsIgnoreCase(topLevelType)) {
                return "image";
            }
        }
        return "image";
    }

    private String patternFor(String normalizedCode) {
        return String.format(Locale.ROOT, CATALOG_MEDIA_PATTERN, normalizedCode);
    }

    private String normalizeCode(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeMediaKind(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public record ResolvedPreviewMedia(String url, String kind) {
        int priority() {
            return "video".equals(kind) ? 0 : 1;
        }
    }
}
