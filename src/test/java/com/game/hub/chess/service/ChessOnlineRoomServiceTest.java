package com.game.hub.chess.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChessOnlineRoomServiceTest {

    @Test
    void joinRoomShouldAssignWhiteThenBlackAndRejectThirdPlayer() {
        ChessOnlineRoomService service = new ChessOnlineRoomService();

        ChessOnlineRoomService.JoinResult first = service.joinRoom("CHESS-1", "u1", "User 1", "");
        ChessOnlineRoomService.JoinResult second = service.joinRoom("CHESS-1", "u2", "User 2", "");
        ChessOnlineRoomService.JoinResult third = service.joinRoom("CHESS-1", "u3", "User 3", "");

        assertTrue(first.ok());
        assertEquals("w", first.assignedColor());
        assertTrue(second.ok());
        assertEquals("b", second.assignedColor());
        assertFalse(third.ok());
        assertEquals("Room is full", third.error());
        assertNotNull(second.room());
        assertEquals(2, second.room().playerCount());
        assertEquals("w", second.room().currentTurnColor());
    }

    @Test
    void moveShouldEnforceTurnAndSwitchToOtherPlayer() {
        ChessOnlineRoomService service = new ChessOnlineRoomService();
        service.joinRoom("CHESS-2", "whiteUser", "W", "");
        service.joinRoom("CHESS-2", "blackUser", "B", "");

        ChessOnlineRoomService.ActionResult okMove = service.move("CHESS-2", "whiteUser", 6, 4, 4, 4, null);

        assertTrue(okMove.ok());
        assertEquals("MOVE", okMove.eventType());
        assertNotNull(okMove.room());
        assertEquals("blackUser", okMove.room().currentTurnUserId());
        assertEquals("b", okMove.room().currentTurnColor());
        assertNull(okMove.room().board()[6][4]);
        assertEquals("wP", okMove.room().board()[4][4]);
        assertEquals(1, okMove.room().moveHistory().size());

        ChessOnlineRoomService.ActionResult wrongTurn = service.move("CHESS-2", "whiteUser", 6, 3, 4, 3, null);
        assertFalse(wrongTurn.ok());
        assertEquals("Not your turn", wrongTurn.error());
    }

    @Test
    void moveShouldRejectIllegalPieceMovement() {
        ChessOnlineRoomService service = new ChessOnlineRoomService();
        service.joinRoom("CHESS-ILLEGAL", "whiteUser", "W", "");
        service.joinRoom("CHESS-ILLEGAL", "blackUser", "B", "");

        ChessOnlineRoomService.ActionResult result = service.move("CHESS-ILLEGAL", "whiteUser", 7, 0, 5, 1, null);

        assertFalse(result.ok());
        assertEquals("Illegal move", result.error());
    }

    @Test
    void foolsMateShouldMarkGameOver() {
        ChessOnlineRoomService service = new ChessOnlineRoomService();
        service.joinRoom("CHESS-MATE", "whiteUser", "White", "");
        service.joinRoom("CHESS-MATE", "blackUser", "Black", "");

        assertTrue(service.move("CHESS-MATE", "whiteUser", 6, 5, 5, 5, null).ok()); // f2-f3
        assertTrue(service.move("CHESS-MATE", "blackUser", 1, 4, 3, 4, null).ok()); // e7-e5
        assertTrue(service.move("CHESS-MATE", "whiteUser", 6, 6, 4, 6, null).ok()); // g2-g4

        ChessOnlineRoomService.ActionResult mate = service.move("CHESS-MATE", "blackUser", 0, 3, 4, 7, null); // Qd8-h4#

        assertTrue(mate.ok());
        assertNotNull(mate.room());
        assertEquals("GAME_OVER", mate.room().status());
        assertNull(mate.room().currentTurnUserId());
        assertTrue(mate.room().statusMessage().contains("Chieu het"));
    }

    @Test
    void leaveRoomShouldKeepRoomWaitingForNewOpponent() {
        ChessOnlineRoomService service = new ChessOnlineRoomService();
        service.joinRoom("CHESS-3", "u2", "U2", "");
        service.joinRoom("CHESS-3", "u1", "U1", "");

        service.leaveRoom("CHESS-3", "u1");

        ChessOnlineRoomService.RoomSnapshot snapshot = service.roomSnapshot("CHESS-3");
        assertNotNull(snapshot);
        assertEquals(1, snapshot.playerCount());
        assertEquals("WAITING", snapshot.status());
        assertNull(snapshot.currentTurnUserId());
        assertEquals(1, service.availableRooms().size());
        assertEquals("CHESS-3", service.availableRooms().getFirst().roomId());
        assertEquals("w", snapshot.players().getFirst().color());
    }

    @Test
    void surrenderShouldMarkGameOverAndBlockFurtherMoves() {
        ChessOnlineRoomService service = new ChessOnlineRoomService();
        service.joinRoom("CHESS-4", "whiteUser", "White", "");
        service.joinRoom("CHESS-4", "blackUser", "Black", "");

        ChessOnlineRoomService.ActionResult surrender = service.surrenderGame("CHESS-4", "whiteUser");

        assertTrue(surrender.ok());
        assertEquals("SURRENDER", surrender.eventType());
        assertNotNull(surrender.room());
        assertEquals("GAME_OVER", surrender.room().status());
        assertNull(surrender.room().currentTurnUserId());
        assertTrue(surrender.room().statusMessage().contains("dau hang"));
        assertEquals("Trang dau hang", surrender.room().moveHistory().getLast());

        ChessOnlineRoomService.ActionResult moveAfterSurrender = service.move("CHESS-4", "blackUser", 1, 4, 3, 4, null);
        assertFalse(moveAfterSurrender.ok());
        assertEquals("Game already ended", moveAfterSurrender.error());
    }
}
