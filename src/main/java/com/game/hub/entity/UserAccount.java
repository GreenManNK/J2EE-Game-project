package com.game.hub.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserAccount {
    @Id
    private String id;

    @Column(unique = true)
    private String username;

    @Column(unique = true)
    private String email;

    @JsonIgnore
    private String password;
    private boolean emailConfirmed = false;
    private String role = "User";
    private String displayName;
    private String avatarPath;
    @JsonIgnore
    @Column(unique = true)
    private String oauthGoogleId;
    @JsonIgnore
    @Column(unique = true)
    private String oauthFacebookId;
    private int score = 50;
    private int highestScore;
    private boolean isOnline = false;
    private LocalDateTime bannedUntil;
    private LocalDateTime lastSystemNotificationSeenAt = LocalDateTime.of(1970, 1, 1, 0, 0);
    private String themeMode = "system";
    private String language = "vi";
    private boolean sidebarDesktopVisibleByDefault = false;
    private boolean sidebarMobileAutoClose = true;
    private boolean homeMusicEnabled = true;
    private boolean toastNotificationsEnabled = true;
    private boolean showOfflineFriendsInSidebar = true;
    private boolean autoRefreshFriendList = true;
    private int friendListRefreshMs = 5000;
    @JsonIgnore
    @Column(length = 4000)
    private String chessOfflineStatsJson;
    @JsonIgnore
    @Column(length = 4000)
    private String xiangqiOfflineStatsJson;
    @JsonIgnore
    @Column(length = 8000)
    private String minesweeperStatsJson;
    @JsonIgnore
    @Column(length = 4000)
    private String puzzleCatalogFavoritesJson;
    @JsonIgnore
    @Column(length = 4000)
    private String puzzleCatalogRatingsJson;
    @JsonIgnore
    @Column(length = 4000)
    private String puzzleCatalogRecentJson;
    @JsonIgnore
    @Column(length = 4000)
    private String gamesBrowserFavoritesJson;
    @JsonIgnore
    @Column(length = 8000)
    private String gamesBrowserRecentJson;

    @PrePersist
    public void prePersist() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
    }

    public boolean isBanned() {
        return bannedUntil != null && bannedUntil.isAfter(LocalDateTime.now());
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public boolean isEmailConfirmed() { return emailConfirmed; }
    public void setEmailConfirmed(boolean emailConfirmed) { this.emailConfirmed = emailConfirmed; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getAvatarPath() { return avatarPath; }
    public void setAvatarPath(String avatarPath) { this.avatarPath = avatarPath; }
    public String getOauthGoogleId() { return oauthGoogleId; }
    public void setOauthGoogleId(String oauthGoogleId) { this.oauthGoogleId = oauthGoogleId; }
    public String getOauthFacebookId() { return oauthFacebookId; }
    public void setOauthFacebookId(String oauthFacebookId) { this.oauthFacebookId = oauthFacebookId; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public int getHighestScore() { return highestScore; }
    public void setHighestScore(int highestScore) { this.highestScore = highestScore; }
    public boolean isOnline() { return isOnline; }
    public void setOnline(boolean online) { isOnline = online; }
    public LocalDateTime getBannedUntil() { return bannedUntil; }
    public void setBannedUntil(LocalDateTime bannedUntil) { this.bannedUntil = bannedUntil; }
    public LocalDateTime getLastSystemNotificationSeenAt() { return lastSystemNotificationSeenAt; }
    public void setLastSystemNotificationSeenAt(LocalDateTime lastSystemNotificationSeenAt) { this.lastSystemNotificationSeenAt = lastSystemNotificationSeenAt; }
    public String getThemeMode() { return themeMode; }
    public void setThemeMode(String themeMode) { this.themeMode = themeMode; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public boolean isSidebarDesktopVisibleByDefault() { return sidebarDesktopVisibleByDefault; }
    public void setSidebarDesktopVisibleByDefault(boolean sidebarDesktopVisibleByDefault) { this.sidebarDesktopVisibleByDefault = sidebarDesktopVisibleByDefault; }
    public boolean isSidebarMobileAutoClose() { return sidebarMobileAutoClose; }
    public void setSidebarMobileAutoClose(boolean sidebarMobileAutoClose) { this.sidebarMobileAutoClose = sidebarMobileAutoClose; }
    public boolean isHomeMusicEnabled() { return homeMusicEnabled; }
    public void setHomeMusicEnabled(boolean homeMusicEnabled) { this.homeMusicEnabled = homeMusicEnabled; }
    public boolean isToastNotificationsEnabled() { return toastNotificationsEnabled; }
    public void setToastNotificationsEnabled(boolean toastNotificationsEnabled) { this.toastNotificationsEnabled = toastNotificationsEnabled; }
    public boolean isShowOfflineFriendsInSidebar() { return showOfflineFriendsInSidebar; }
    public void setShowOfflineFriendsInSidebar(boolean showOfflineFriendsInSidebar) { this.showOfflineFriendsInSidebar = showOfflineFriendsInSidebar; }
    public boolean isAutoRefreshFriendList() { return autoRefreshFriendList; }
    public void setAutoRefreshFriendList(boolean autoRefreshFriendList) { this.autoRefreshFriendList = autoRefreshFriendList; }
    public int getFriendListRefreshMs() { return friendListRefreshMs; }
    public void setFriendListRefreshMs(int friendListRefreshMs) { this.friendListRefreshMs = friendListRefreshMs; }
    public String getChessOfflineStatsJson() { return chessOfflineStatsJson; }
    public void setChessOfflineStatsJson(String chessOfflineStatsJson) { this.chessOfflineStatsJson = chessOfflineStatsJson; }
    public String getXiangqiOfflineStatsJson() { return xiangqiOfflineStatsJson; }
    public void setXiangqiOfflineStatsJson(String xiangqiOfflineStatsJson) { this.xiangqiOfflineStatsJson = xiangqiOfflineStatsJson; }
    public String getMinesweeperStatsJson() { return minesweeperStatsJson; }
    public void setMinesweeperStatsJson(String minesweeperStatsJson) { this.minesweeperStatsJson = minesweeperStatsJson; }
    public String getPuzzleCatalogFavoritesJson() { return puzzleCatalogFavoritesJson; }
    public void setPuzzleCatalogFavoritesJson(String puzzleCatalogFavoritesJson) { this.puzzleCatalogFavoritesJson = puzzleCatalogFavoritesJson; }
    public String getPuzzleCatalogRatingsJson() { return puzzleCatalogRatingsJson; }
    public void setPuzzleCatalogRatingsJson(String puzzleCatalogRatingsJson) { this.puzzleCatalogRatingsJson = puzzleCatalogRatingsJson; }
    public String getPuzzleCatalogRecentJson() { return puzzleCatalogRecentJson; }
    public void setPuzzleCatalogRecentJson(String puzzleCatalogRecentJson) { this.puzzleCatalogRecentJson = puzzleCatalogRecentJson; }
    public String getGamesBrowserFavoritesJson() { return gamesBrowserFavoritesJson; }
    public void setGamesBrowserFavoritesJson(String gamesBrowserFavoritesJson) { this.gamesBrowserFavoritesJson = gamesBrowserFavoritesJson; }
    public String getGamesBrowserRecentJson() { return gamesBrowserRecentJson; }
    public void setGamesBrowserRecentJson(String gamesBrowserRecentJson) { this.gamesBrowserRecentJson = gamesBrowserRecentJson; }
}
