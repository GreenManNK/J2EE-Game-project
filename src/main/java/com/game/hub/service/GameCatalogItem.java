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
    List<String> roadmapItems
) {
    public boolean hasPrimaryAction() {
        return primaryActionUrl != null && !primaryActionUrl.isBlank();
    }
}
