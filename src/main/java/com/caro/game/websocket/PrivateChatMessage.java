package com.caro.game.websocket;

public class PrivateChatMessage {
    private String currentUserId;
    private String friendId;
    private String senderName;
    private String content;

    public String getCurrentUserId() { return currentUserId; }
    public void setCurrentUserId(String currentUserId) { this.currentUserId = currentUserId; }
    public String getFriendId() { return friendId; }
    public void setFriendId(String friendId) { this.friendId = friendId; }
    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
