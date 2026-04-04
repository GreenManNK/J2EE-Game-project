package com.game.hub.service;

import com.game.hub.entity.AchievementNotification;
import com.game.hub.entity.GameHistory;
import com.game.hub.entity.UserAchievement;
import com.game.hub.entity.UserAccount;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        WinRewardScoreService winRewardScoreService = mock(WinRewardScoreService.class);

        AchievementService service = new AchievementService(
            userAchievementRepository,
            achievementNotificationRepository,
            userAccountRepository,
            gameHistoryRepository,
            winRewardScoreService
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
        WinRewardScoreService winRewardScoreService = mock(WinRewardScoreService.class);

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
            gameHistoryRepository,
            winRewardScoreService
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

    @Test
    void evaluateAfterMatchShouldIgnoreBotHistoriesForCompetitiveMilestones() {
        UserAchievementRepository userAchievementRepository = mock(UserAchievementRepository.class);
        AchievementNotificationRepository achievementNotificationRepository = mock(AchievementNotificationRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        GameHistoryRepository gameHistoryRepository = mock(GameHistoryRepository.class);
        WinRewardScoreService winRewardScoreService = mock(WinRewardScoreService.class);

        List<UserAchievement> savedAchievements = new ArrayList<>();
        when(userAchievementRepository.existsByUserIdAndAchievementName(anyString(), anyString())).thenReturn(false);
        when(userAchievementRepository.save(any(UserAchievement.class))).thenAnswer(invocation -> {
            UserAchievement achievement = invocation.getArgument(0, UserAchievement.class);
            savedAchievements.add(achievement);
            return achievement;
        });
        when(achievementNotificationRepository.save(any(AchievementNotification.class)))
            .thenAnswer(invocation -> invocation.getArgument(0, AchievementNotification.class));

        UserAccount winner = new UserAccount();
        winner.setId("winner-1");
        winner.setScore(0);

        UserAccount loser = new UserAccount();
        loser.setId("loser-1");
        loser.setScore(0);

        when(userAccountRepository.findById("winner-1")).thenReturn(java.util.Optional.of(winner));
        when(userAccountRepository.findById("loser-1")).thenReturn(java.util.Optional.of(loser));
        when(userAccountRepository.existsById("winner-1")).thenReturn(true);
        when(userAccountRepository.existsById("loser-1")).thenReturn(true);

        List<GameHistory> winnerGames = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            GameHistory history = new GameHistory();
            history.setGameCode("chess-bot");
            history.setPlayer1Id("winner-1");
            history.setPlayer2Id("bot-chess-hard");
            history.setWinnerId("winner-1");
            winnerGames.add(history);
        }
        when(gameHistoryRepository.findByPlayer1IdOrPlayer2IdOrderByPlayedAtDesc("winner-1", "winner-1")).thenReturn(winnerGames);
        when(gameHistoryRepository.findByPlayer1IdOrPlayer2IdOrderByPlayedAtDesc("loser-1", "loser-1")).thenReturn(List.of());

        AchievementService service = new AchievementService(
            userAchievementRepository,
            achievementNotificationRepository,
            userAccountRepository,
            gameHistoryRepository,
            winRewardScoreService
        );

        service.evaluateAfterMatch("Normal_ROOM", "winner-1", "loser-1", 12, null, null, null);

        assertTrue(savedAchievements.isEmpty());
    }

    @Test
    void recordRewardedWinShouldGrantAchievementAndAwardScoreBonus() {
        UserAchievementRepository userAchievementRepository = mock(UserAchievementRepository.class);
        AchievementNotificationRepository achievementNotificationRepository = mock(AchievementNotificationRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        GameHistoryRepository gameHistoryRepository = mock(GameHistoryRepository.class);
        WinRewardScoreService winRewardScoreService = mock(WinRewardScoreService.class);
        UserAccount user = new UserAccount();
        user.setId("user-1");
        user.setChessWinCount(9);

        when(userAccountRepository.existsById("user-1")).thenReturn(true);
        when(userAccountRepository.findById("user-1")).thenReturn(java.util.Optional.of(user));
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0, UserAccount.class));
        when(userAchievementRepository.existsByUserIdAndAchievementName("user-1", "Winner - Chess")).thenReturn(false);
        when(userAchievementRepository.findByUserId("user-1")).thenReturn(List.of());
        when(userAchievementRepository.save(any(UserAchievement.class))).thenAnswer(invocation -> invocation.getArgument(0, UserAchievement.class));
        when(achievementNotificationRepository.save(any(AchievementNotification.class))).thenAnswer(invocation -> invocation.getArgument(0, AchievementNotification.class));

        AchievementService service = new AchievementService(
            userAchievementRepository,
            achievementNotificationRepository,
            userAccountRepository,
            gameHistoryRepository,
            winRewardScoreService
        );

        service.recordRewardedWin("user-1", "Chess");

        verify(userAchievementRepository).save(any(UserAchievement.class));
        verify(winRewardScoreService).awardPlayerWinBonus("user-1");
        assertEquals(10, user.getChessWinCount());
    }

    @Test
    void unlockFlamingChessIconShouldRequireTenRecordedChessWins() {
        UserAchievementRepository userAchievementRepository = mock(UserAchievementRepository.class);
        AchievementNotificationRepository achievementNotificationRepository = mock(AchievementNotificationRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        GameHistoryRepository gameHistoryRepository = mock(GameHistoryRepository.class);
        WinRewardScoreService winRewardScoreService = mock(WinRewardScoreService.class);
        UserAccount user = new UserAccount();
        user.setId("user-1");
        user.setChessWinCount(10);

        when(userAccountRepository.existsById("user-1")).thenReturn(true);
        when(userAccountRepository.findById("user-1")).thenReturn(java.util.Optional.of(user));
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0, UserAccount.class));
        when(userAchievementRepository.existsByUserIdAndAchievementName("user-1", AchievementService.FLAMING_CHESS_ICON_ACHIEVEMENT)).thenReturn(false);
        when(userAchievementRepository.save(any(UserAchievement.class))).thenAnswer(invocation -> invocation.getArgument(0, UserAchievement.class));
        when(achievementNotificationRepository.save(any(AchievementNotification.class))).thenAnswer(invocation -> invocation.getArgument(0, AchievementNotification.class));

        AchievementService service = new AchievementService(
            userAchievementRepository,
            achievementNotificationRepository,
            userAccountRepository,
            gameHistoryRepository,
            winRewardScoreService
        );

        AchievementService.ChessIconUnlockResult success = service.unlockFlamingChessIcon("user-1");

        assertTrue(success.success());
        assertFalse(success.alreadyUnlocked());
        assertTrue(user.isFlamingChessIconUnlocked());
        verify(userAchievementRepository).save(any(UserAchievement.class));
    }
}
