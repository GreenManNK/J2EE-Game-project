package com.game.hub.service;

import com.game.hub.entity.UserAccount;
import com.game.hub.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WinRewardScoreServiceTest {

    @Test
    void shouldAwardFixedBonusAndRaiseHighestScore() {
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        UserAccount user = new UserAccount();
        user.setId("user-1");
        user.setScore(70);
        user.setHighestScore(72);
        when(userAccountRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0, UserAccount.class));

        WinRewardScoreService service = new WinRewardScoreService(userAccountRepository);

        WinRewardScoreService.RewardResult result = service.awardPlayerWinBonus("user-1");

        assertTrue(result.awarded());
        assertEquals(WinRewardScoreService.PLAYER_WIN_BONUS_POINTS, result.awardedPoints());
        assertEquals(75, result.newScore());
        assertEquals(75, user.getScore());
        assertEquals(75, user.getHighestScore());
        verify(userAccountRepository).save(user);
    }

    @Test
    void shouldSkipMissingUser() {
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        when(userAccountRepository.findById("ghost")).thenReturn(Optional.empty());

        WinRewardScoreService service = new WinRewardScoreService(userAccountRepository);

        WinRewardScoreService.RewardResult result = service.awardPlayerWinBonus("ghost");

        assertFalse(result.awarded());
        assertEquals(0, result.newScore());
        verify(userAccountRepository, never()).save(any(UserAccount.class));
    }
}
