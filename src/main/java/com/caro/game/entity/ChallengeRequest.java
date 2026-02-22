package com.caro.game.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.time.LocalDateTime;

@Entity
public class ChallengeRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String fromUserId;
    private String toUserId;
    private String roomId;
    private LocalDateTime sentAt;
    private boolean accepted;
    private boolean handled;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFromUserId() { return fromUserId; }
    public void setFromUserId(String fromUserId) { this.fromUserId = fromUserId; }
    public String getToUserId() { return toUserId; }
    public void setToUserId(String toUserId) { this.toUserId = toUserId; }
    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
    public boolean isAccepted() { return accepted; }
    public void setAccepted(boolean accepted) { this.accepted = accepted; }
    public boolean isHandled() { return handled; }
    public void setHandled(boolean handled) { this.handled = handled; }
}