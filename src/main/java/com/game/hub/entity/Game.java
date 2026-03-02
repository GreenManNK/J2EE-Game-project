package com.game.hub.entity;

public class Game {
    private String code;
    private String displayName;
    private String description;
    private String iconClass;
    private boolean supportsOnline;
    private boolean supportsOffline;
    private boolean supportsGuest;
    private String primaryActionUrl;

    public Game(String code, String displayName, String description, String iconClass, boolean supportsOnline, boolean supportsOffline, boolean supportsGuest, String primaryActionUrl) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
        this.iconClass = iconClass;
        this.supportsOnline = supportsOnline;
        this.supportsOffline = supportsOffline;
        this.supportsGuest = supportsGuest;
        this.primaryActionUrl = primaryActionUrl;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String getIconClass() {
        return iconClass;
    }

    public boolean isSupportsOnline() {
        return supportsOnline;
    }

    public boolean isSupportsOffline() {
        return supportsOffline;
    }

    public boolean isSupportsGuest() {
        return supportsGuest;
    }

    public String getPrimaryActionUrl() {
        return primaryActionUrl;
    }
}
