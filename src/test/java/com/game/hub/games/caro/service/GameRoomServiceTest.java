package com.game.hub.games.caro.service;

import com.game.hub.repository.GameHistoryRepository;
import com.game.hub.repository.UserAccountRepository;
import com.game.hub.service.AchievementService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void leaveRoomShouldResetBoardAndAllowNewPlayerToGetMissingSymbol() {
        GameRoomService service = new GameRoomService(
            mock(UserAccountRepository.class),
            mock(GameHistoryRepository.class),
            mock(AchievementService.class)
        );

        GameRoomService.JoinResult first = service.joinRoom("room-b", "u1");
        GameRoomService.JoinResult second = service.joinRoom("room-b", "u2");
        assertTrue(first.ok());
        assertTrue(second.ok());
        assertEquals("X", first.symbol());
        assertEquals("O", second.symbol());

        GameRoomService.MoveResult move = service.makeMove("room-b", "u1", 0, 0);
        assertTrue(move.ok());
        assertEquals("X", service.getBoardSnapshot("room-b")[0][0]);

        service.leaveRoom("room-b", "u1");

        assertNull(service.getCurrentTurnUserId("room-b"));
        assertNull(service.getBoardSnapshot("room-b")[0][0]);

        GameRoomService.JoinResult third = service.joinRoom("room-b", "u3");
        assertTrue(third.ok());
        assertEquals("X", third.symbol());
        assertEquals("u3", third.currentTurnUserId());
    }
}
