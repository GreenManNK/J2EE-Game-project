package com.game.hub.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.time.LocalDateTime;

@Entity
public class UserAchievement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String userId;
    private String achievementName;
    private LocalDateTime achievedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getAchievementName() { return achievementName; }
    public void setAchievementName(String achievementName) { this.achievementName = achievementName; }
    public LocalDateTime getAchievedAt() { return achievedAt; }
    public void setAchievedAt(LocalDateTime achievedAt) { this.achievedAt = achievedAt; }
}