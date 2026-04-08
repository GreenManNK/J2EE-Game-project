package com.game.hub.service;

import com.game.hub.entity.GameHistory;
import com.game.hub.entity.UserAchievement;
import com.game.hub.entity.UserAccount;
import com.game.hub.repository.GameHistoryRepository;
import com.game.hub.repository.UserAchievementRepository;
import com.game.hub.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileStatsServiceTest {

    @Test
    void buildProfileStatsShouldGroupOverallAndPerGameAchievements() {
        GameHistoryRepository gameHistoryRepository = mock(GameHistoryRepository.class);
        UserAchievementRepository userAchievementRepository = mock(UserAchievementRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);

        UserAccount user = new UserAccount();
        user.setId("user-1");
        user.setDisplayName("Player One");
        user.setGamesBrowserFavoritesJson("[\"caro\",\"quiz\"]");
        user.setChessOfflineStatsJson("""
            {"whiteWins":4,"blackWins":2,"draws":1}
            """);
        user.setXiangqiOfflineStatsJson("""
            {"redWins":3,"blackWins":1,"draws":2}
            """);
        user.setMinesweeperStatsJson("""
            {"totalGames":6,"wins":4,"losses":2,"bestTimes":{"preset:beginner":38,"preset:expert":121}}
            """);
        user.setQuizPracticeStatsJson("""
            {"totalGames":5,"wins":3,"losses":1,"draws":1,"bestScore":18,"perfectRounds":2}
            """);
        user.setTypingPracticeStatsJson("""
            {"totalGames":4,"wins":2,"losses":1,"draws":1,"bestWpm":77,"bestAccuracy":98.2,"completedQuotes":3}
            """);

        GameHistory win = new GameHistory();
        win.setId(15L);
        win.setGameCode("caro");
        win.setMatchCode("Normal_ABC123-1743041234567");
        win.setRoomId("Normal_ABC123");
        win.setLocationLabel("Phong thuong Caro");
        win.setLocationPath("/game/room/Normal_ABC123");
        win.setPlayer1Id("user-1");
        win.setPlayer2Id("user-2");
        win.setWinnerId("user-1");
        win.setTotalMoves(12);

        when(userAccountRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(userAccountRepository.findAllByOrderByScoreDesc()).thenReturn(List.of(user));
        when(gameHistoryRepository.findByPlayer1IdOrPlayer2IdOrderByPlayedAtDesc("user-1", "user-1"))
            .thenReturn(List.of(win));
        when(userAchievementRepository.findByUserId("user-1")).thenReturn(List.of(
            achievement("Bac"),
            achievement("Khoi dong da game"),
            achievement("Winner - Caro"),
            achievement("Winner - Quiz"),
            achievement("Ke co chap")
        ));

        ProfileStatsService service = new ProfileStatsService(
            gameHistoryRepository,
            userAchievementRepository,
            userAccountRepository
        );

        Map<String, Object> profile = service.buildProfileStats("user-1", "user-1");

        assertEquals(List.of("Khoi dong da game"), profile.get("overallAchievements"));

        @SuppressWarnings("unchecked")
        Map<String, List<String>> gameAchievements = (Map<String, List<String>>) profile.get("gameAchievements");
        assertEquals(List.of("Chien thang dau tien"), gameAchievements.get("Caro"));
        assertEquals(List.of("Chien thang dau tien"), gameAchievements.get("Quiz"));

        @SuppressWarnings("unchecked")
        Map<String, Long> repeatAchievements = (Map<String, Long>) profile.get("repeatAchievements");
        assertEquals(1L, repeatAchievements.get("Ke co chap"));

        @SuppressWarnings("unchecked")
        List<String> lockedAchievements = (List<String>) profile.get("lockedAchievements");
        assertTrue(lockedAchievements.contains("Winner - Chess"));
        assertTrue(lockedAchievements.contains("Bao trum Game Hub"));
        assertEquals(Boolean.TRUE, profile.get("showAchievements"));
        assertEquals(2, profile.get("favoriteGameCount"));
        assertEquals(1, profile.get("currentStreak"));

        @SuppressWarnings("unchecked")
        List<ProfileStatsService.ActivityItem> recentActivity =
            (List<ProfileStatsService.ActivityItem>) profile.get("recentActivity");
        assertEquals("Normal_ABC123-1743041234567", recentActivity.get(0).matchCode());
        assertEquals("Phong thuong Caro", recentActivity.get(0).locationLabel());
        assertEquals("/game/room/Normal_ABC123", recentActivity.get(0).locationHref());

        @SuppressWarnings("unchecked")
        List<ProfileStatsService.PracticeStatCard> practiceStatCards =
            (List<ProfileStatsService.PracticeStatCard>) profile.get("practiceStatCards");
        assertEquals(5, practiceStatCards.size());
        assertEquals("chess-offline", practiceStatCards.get(0).code());
        assertEquals("4/2", practiceStatCards.get(0).primaryValue());
        assertEquals("xiangqi-offline", practiceStatCards.get(1).code());
        assertEquals("3/1", practiceStatCards.get(1).primaryValue());
        assertEquals("minesweeper", practiceStatCards.get(2).code());
        assertEquals("38s", practiceStatCards.get(2).secondaryValue());
        assertEquals("quiz-practice", practiceStatCards.get(3).code());
        assertEquals("18", practiceStatCards.get(3).primaryValue());
        assertEquals("typing-practice", practiceStatCards.get(4).code());
        assertEquals("77", practiceStatCards.get(4).primaryValue());
        assertEquals(5L, profile.get("practiceProgressCount"));
    }

    @Test
    void buildProfileStatsShouldHideAchievementsForGuestIds() {
        GameHistoryRepository gameHistoryRepository = mock(GameHistoryRepository.class);
        UserAchievementRepository userAchievementRepository = mock(UserAchievementRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);

        UserAccount user = new UserAccount();
        user.setId("guest-123");
        user.setDisplayName("Guest");

        when(userAccountRepository.findById("guest-123")).thenReturn(Optional.of(user));
        when(userAccountRepository.findAllByOrderByScoreDesc()).thenReturn(List.of(user));
        when(gameHistoryRepository.findByPlayer1IdOrPlayer2IdOrderByPlayedAtDesc("guest-123", "guest-123"))
            .thenReturn(List.of());

        ProfileStatsService service = new ProfileStatsService(
            gameHistoryRepository,
            userAchievementRepository,
            userAccountRepository
        );

        Map<String, Object> profile = service.buildProfileStats("guest-123", "guest-123");

        assertEquals(Boolean.FALSE, profile.get("showAchievements"));
        assertTrue(((List<?>) profile.get("lockedAchievements")).isEmpty());
        assertTrue(((Map<?, ?>) profile.get("gameAchievements")).isEmpty());
        verify(userAchievementRepository, never()).findByUserId("guest-123");
    }

    private UserAchievement achievement(String achievementName) {
        UserAchievement userAchievement = new UserAchievement();
        userAchievement.setAchievementName(achievementName);
        return userAchievement;
    }
}
