package com.game.hub.service;

import com.game.hub.entity.AchievementNotification;
import com.game.hub.entity.UserAchievement;
import com.game.hub.repository.AchievementNotificationRepository;
import com.game.hub.repository.GameHistoryRepository;
import com.game.hub.repository.UserAchievementRepository;
import com.game.hub.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AchievementServiceTest {

    @Test
    void checkAndAwardShouldSkipGuestAccount() {
        UserAchievementRepository userAchievementRepository = mock(UserAchievementRepository.class);
        AchievementNotificationRepository achievementNotificationRepository = mock(AchievementNotificationRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        GameHistoryRepository gameHistoryRepository = mock(GameHistoryRepository.class);

        AchievementService service = new AchievementService(
            userAchievementRepository,
            achievementNotificationRepository,
            userAccountRepository,
            gameHistoryRepository
        );

        service.checkAndAward("guest-123", "Caro", true);

        verify(userAchievementRepository, never()).save(any(UserAchievement.class));
        verify(achievementNotificationRepository, never()).save(any(AchievementNotification.class));
    }

    @Test
    void checkAndAwardShouldGrantOverallCoverageAchievementsForRealAccounts() {
        UserAchievementRepository userAchievementRepository = mock(UserAchievementRepository.class);
        AchievementNotificationRepository achievementNotificationRepository = mock(AchievementNotificationRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        GameHistoryRepository gameHistoryRepository = mock(GameHistoryRepository.class);

        List<UserAchievement> savedAchievements = new ArrayList<>();

        when(userAccountRepository.existsById("user-1")).thenReturn(true);
        when(userAchievementRepository.findByUserId("user-1")).thenAnswer(invocation -> new ArrayList<>(savedAchievements));
        when(userAchievementRepository.existsByUserIdAndAchievementName(eq("user-1"), anyString()))
            .thenAnswer(invocation -> {
                String achievementName = invocation.getArgument(1, String.class);
                return savedAchievements.stream().anyMatch(item -> achievementName.equals(item.getAchievementName()));
            });
        when(userAchievementRepository.save(any(UserAchievement.class))).thenAnswer(invocation -> {
            UserAchievement achievement = invocation.getArgument(0, UserAchievement.class);
            savedAchievements.add(achievement);
            return achievement;
        });
        when(achievementNotificationRepository.save(any(AchievementNotification.class)))
            .thenAnswer(invocation -> invocation.getArgument(0, AchievementNotification.class));

        AchievementService service = new AchievementService(
            userAchievementRepository,
            achievementNotificationRepository,
            userAccountRepository,
            gameHistoryRepository
        );

        List.of(
            "Caro",
            "Chess",
            "Xiangqi",
            "Tien Len",
            "Typing",
            "Quiz",
            "Blackjack",
            "Minesweeper",
            "Puzzle Jigsaw",
            "Puzzle Sliding",
            "Puzzle Word",
            "Puzzle Sudoku"
        ).forEach(gameName -> service.checkAndAward("user-1", gameName, true));

        Set<String> unlocked = savedAchievements.stream()
            .map(UserAchievement::getAchievementName)
            .collect(Collectors.toSet());

        assertTrue(unlocked.contains("Khoi dong da game"));
        assertTrue(unlocked.contains("Chinh phuc 6 game"));
        assertTrue(unlocked.contains("Chinh phuc 9 game"));
        assertTrue(unlocked.contains("Bao trum Game Hub"));
        assertTrue(unlocked.contains("Hoan tat Puzzle Pack"));
        assertEquals(1L, savedAchievements.stream().filter(item -> "Bao trum Game Hub".equals(item.getAchievementName())).count());
    }
}
