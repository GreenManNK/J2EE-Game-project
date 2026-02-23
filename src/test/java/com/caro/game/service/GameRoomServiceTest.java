package com.caro.game.service;

import com.caro.game.repository.GameHistoryRepository;
import com.caro.game.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class GameRoomServiceTest {

    @Test
    void shouldRejectMoveWhenRoomHasOnlyOnePlayer() {
        GameRoomService service = new GameRoomService(
            mock(UserAccountRepository.class),
            mock(GameHistoryRepository.class),
            mock(AchievementService.class)
        );

        GameRoomService.JoinResult join = service.joinRoom("room-a", "u1");
        assertTrue(join.ok());
        assertEquals("u1", join.currentTurnUserId());
        assertEquals(1, join.playerCount());

        GameRoomService.MoveResult move = service.makeMove("room-a", "u1", 0, 0);
        assertFalse(move.ok());
        assertEquals("Waiting for opponent", move.error());
    }
}
