package com.game.hub.caro.websocket;

public class ChatMessage {
    private String roomId;
    private String userId;
    private String displayName;
    private String avatarPath;
    private String content;

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getAvatarPath() { return avatarPath; }
    public void setAvatarPath(String avatarPath) { this.avatarPath = avatarPath; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}