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

        GameHistory win = new GameHistory();
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
