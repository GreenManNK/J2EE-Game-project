package com.game.hub.service;

import java.util.List;

public record GameCatalogItem(
    String code,
    String displayName,
    String shortLabel,
    String description,
    String iconClass,
    boolean availableNow,
    boolean supportsOnline,
    boolean supportsOffline,
    boolean supportsGuest,
    String primaryActionLabel,
    String primaryActionUrl,
    List<String> roadmapItems,
    String sourceType,
    String runtime,
    String detailMode,
    String embedUrl,
    String apiBaseUrl,
    String manifestUrl,
    boolean overrideExisting
) {
    public GameCatalogItem(
        String code,
        String displayName,
        String shortLabel,
        String description,
        String iconClass,
        boolean availableNow,
        boolean supportsOnline,
        boolean supportsOffline,
        boolean supportsGuest,
        String primaryActionLabel,
        String primaryActionUrl,
        List<String> roadmapItems
    ) {
        this(
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
            "native",
            "java",
            "native",
            "",
            "",
            "",
            false
        );
    }

    public boolean hasPrimaryAction() {
        return primaryActionUrl != null && !primaryActionUrl.isBlank();
    }

    public boolean isExternalSource() {
        return sourceType != null && !"native".equalsIgnoreCase(sourceType.trim());
    }

    public boolean primaryActionIsExternal() {
        return isAbsoluteHttpUrl(primaryActionUrl);
    }

    public boolean hasEmbedUrl() {
        return embedUrl != null && !embedUrl.isBlank();
    }

    public boolean embedUrlIsExternal() {
        return isAbsoluteHttpUrl(embedUrl);
    }

    public boolean hasExternalApi() {
        return apiBaseUrl != null && !apiBaseUrl.isBlank();
    }

    public boolean usesExternalDetailView() {
        return isExternalSource();
    }

    public String proxyApiBasePath() {
        if (!hasExternalApi() || code == null || code.isBlank()) {
            return "";
        }
        return "/games/external/" + code.trim().toLowerCase() + "/api";
    }

    private boolean isAbsoluteHttpUrl(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return normalized.startsWith("http://") || normalized.startsWith("https://");
    }
}
