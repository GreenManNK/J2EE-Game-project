package com.game.hub.repository;

import com.game.hub.entity.AchievementNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AchievementNotificationRepository extends JpaRepository<AchievementNotification, Long> {
    @Query("select a from AchievementNotification a where a.userId = :userId and a.isRead = false")
    List<AchievementNotification> findUnreadByUserId(@Param("userId") String userId);
    List<AchievementNotification> findByUserIdOrderByCreatedAtDesc(String userId);
}
