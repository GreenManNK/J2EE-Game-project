package com.game.hub.service;

import java.util.List;

public record ExternalGameModuleConfig(
    String code,
    String displayName,
    String shortLabel,
    String description,
    String iconClass,
    Boolean availableNow,
    Boolean supportsOnline,
    Boolean supportsOffline,
    Boolean supportsGuest,
    String primaryActionLabel,
    String primaryActionUrl,
    List<String> roadmapItems,
    String sourceType,
    String runtime,
    String detailMode,
    String embedUrl,
    String apiBaseUrl,
    String manifestUrl,
    Boolean overrideExisting
) {
}
