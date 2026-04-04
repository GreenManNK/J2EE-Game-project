package com.game.hub.service;

import com.game.hub.entity.UserAccount;
import com.game.hub.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WinRewardScoreService {
    public static final int PLAYER_WIN_BONUS_POINTS = 5;

    private final UserAccountRepository userAccountRepository;

    public WinRewardScoreService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional
    public RewardResult awardPlayerWinBonus(String userId) {
        String normalizedUserId = trimToNull(userId);
        if (normalizedUserId == null) {
            return new RewardResult(false, 0, 0);
        }

        UserAccount user = userAccountRepository.findById(normalizedUserId).orElse(null);
        if (user == null) {
            return new RewardResult(false, 0, 0);
        }

        int nextScore = Math.max(0, user.getScore()) + PLAYER_WIN_BONUS_POINTS;
        user.setScore(nextScore);
        if (nextScore > user.getHighestScore()) {
            user.setHighestScore(nextScore);
        }
        userAccountRepository.save(user);
        return new RewardResult(true, PLAYER_WIN_BONUS_POINTS, nextScore);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public record RewardResult(boolean awarded, int awardedPoints, int newScore) {
    }
}
