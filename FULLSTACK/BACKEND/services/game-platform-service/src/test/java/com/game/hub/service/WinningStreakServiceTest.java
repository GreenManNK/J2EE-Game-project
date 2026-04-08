package com.game.hub.service;

import com.game.hub.entity.UserAccount;
import com.game.hub.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WinningStreakServiceTest {

    @Test
    void recordMatchResultShouldIncrementWinnerAndResetLoser() {
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        WinningStreakService service = new WinningStreakService(userAccountRepository);

        UserAccount winner = new UserAccount();
        winner.setId("winner");
        winner.setWinningStreak(10);
        UserAccount loser = new UserAccount();
        loser.setId("loser");
        loser.setWinningStreak(4);

        when(userAccountRepository.findById("winner")).thenReturn(Optional.of(winner));
        when(userAccountRepository.findById("loser")).thenReturn(Optional.of(loser));
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WinningStreakService.MatchResult result = service.recordMatchResult("winner", "loser");

        assertNotNull(result.winner());
        assertEquals(11, result.winner().winningStreak());
        assertTrue(result.winner().redChessPieces());
        assertNotNull(result.loser());
        assertEquals(0, result.loser().winningStreak());
        assertFalse(result.loser().redChessPieces());
        verify(userAccountRepository).save(winner);
        verify(userAccountRepository).save(loser);
    }
}
