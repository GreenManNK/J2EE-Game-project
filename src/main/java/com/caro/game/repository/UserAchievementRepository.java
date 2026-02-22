package com.caro.game.repository;

import com.caro.game.entity.UserAchievement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserAchievementRepository extends JpaRepository<UserAchievement, Long> {
    List<UserAchievement> findByUserId(String userId);
    boolean existsByUserIdAndAchievementName(String userId, String achievementName);
}
