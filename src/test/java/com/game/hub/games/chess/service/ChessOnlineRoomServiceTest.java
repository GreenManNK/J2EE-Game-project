package com.game.hub.games.chess.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

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
        assertEquals("CHESS-3", service.availableRooms().get(0).roomId());
        assertEquals("w", snapshot.players().get(0).color());
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
        assertEquals("Trang dau hang", surrender.room().moveHistory().get(surrender.room().moveHistory().size() - 1));

        ChessOnlineRoomService.ActionResult moveAfterSurrender = service.move("CHESS-4", "blackUser", 1, 4, 3, 4, null);
        assertFalse(moveAfterSurrender.ok());
        assertEquals("Game already ended", moveAfterSurrender.error());
    }

    @Test
    void spectatorShouldJoinFullRoomWithoutTakingPlayerSeat() {
        ChessOnlineRoomService service = new ChessOnlineRoomService();
        service.joinRoom("CHESS-SPEC", "whiteUser", "White", "");
        service.joinRoom("CHESS-SPEC", "blackUser", "Black", "");

        ChessOnlineRoomService.JoinResult spectator = service.joinAsSpectator("CHESS-SPEC", "viewer-1");

        assertTrue(spectator.ok());
        assertEquals("spectator", spectator.assignedColor());
        assertNotNull(spectator.room());
        assertEquals(2, spectator.room().playerCount());
        assertEquals(1, spectator.room().spectatorCount());
    }

    @Test
    void spectatorLeaveShouldNotResetCurrentBoardState() {
        ChessOnlineRoomService service = new ChessOnlineRoomService();
        service.joinRoom("CHESS-SPEC-LEAVE", "whiteUser", "White", "");
        service.joinRoom("CHESS-SPEC-LEAVE", "blackUser", "Black", "");
        service.move("CHESS-SPEC-LEAVE", "whiteUser", 6, 4, 4, 4, null);
        service.joinAsSpectator("CHESS-SPEC-LEAVE", "viewer-1");

        service.leaveRoom("CHESS-SPEC-LEAVE", "viewer-1");
        ChessOnlineRoomService.RoomSnapshot snapshot = service.roomSnapshot("CHESS-SPEC-LEAVE");

        assertNotNull(snapshot);
        assertEquals("PLAYING", snapshot.status());
        assertEquals("blackUser", snapshot.currentTurnUserId());
        assertEquals(1, snapshot.moveHistory().size());
        assertEquals("wP", snapshot.board()[4][4]);
        assertNull(snapshot.board()[6][4]);
        assertEquals(0, snapshot.spectatorCount());
    }

    @Test
    void kingSideCastlingShouldMoveKingAndRookTogether() {
        ChessOnlineRoomService service = new ChessOnlineRoomService();
        service.joinRoom("CHESS-CASTLE", "whiteUser", "White", "");
        service.joinRoom("CHESS-CASTLE", "blackUser", "Black", "");

        assertTrue(service.move("CHESS-CASTLE", "whiteUser", 7, 6, 5, 5, null).ok()); // Ng1-f3
        assertTrue(service.move("CHESS-CASTLE", "blackUser", 0, 6, 2, 5, null).ok()); // Ng8-f6
        assertTrue(service.move("CHESS-CASTLE", "whiteUser", 6, 4, 5, 4, null).ok()); // e2-e3
        assertTrue(service.move("CHESS-CASTLE", "blackUser", 1, 4, 2, 4, null).ok()); // e7-e6
        assertTrue(service.move("CHESS-CASTLE", "whiteUser", 7, 5, 6, 4, null).ok()); // Bf1-e2
        assertTrue(service.move("CHESS-CASTLE", "blackUser", 0, 5, 1, 4, null).ok()); // Bf8-e7

        ChessOnlineRoomService.ActionResult castle = service.move("CHESS-CASTLE", "whiteUser", 7, 4, 7, 6, null);

        assertTrue(castle.ok());
        assertNotNull(castle.room());
        assertEquals("wK", castle.room().board()[7][6]);
        assertEquals("wR", castle.room().board()[7][5]);
        assertNull(castle.room().board()[7][4]);
        assertNull(castle.room().board()[7][7]);
        assertTrue(castle.room().moveHistory().get(castle.room().moveHistory().size() - 1).contains("nhap thanh ngan"));
    }

    @Test
    void enPassantShouldCapturePawnPassedTwoSquares() {
        ChessOnlineRoomService service = new ChessOnlineRoomService();
        service.joinRoom("CHESS-ENPASSANT", "whiteUser", "White", "");
        service.joinRoom("CHESS-ENPASSANT", "blackUser", "Black", "");

        assertTrue(service.move("CHESS-ENPASSANT", "whiteUser", 6, 4, 4, 4, null).ok()); // e2-e4
        assertTrue(service.move("CHESS-ENPASSANT", "blackUser", 1, 0, 2, 0, null).ok()); // a7-a6
        assertTrue(service.move("CHESS-ENPASSANT", "whiteUser", 4, 4, 3, 4, null).ok()); // e4-e5
        assertTrue(service.move("CHESS-ENPASSANT", "blackUser", 1, 3, 3, 3, null).ok()); // d7-d5

        ChessOnlineRoomService.ActionResult enPassant = service.move("CHESS-ENPASSANT", "whiteUser", 3, 4, 2, 3, null);

        assertTrue(enPassant.ok());
        assertNotNull(enPassant.room());
        assertEquals("wP", enPassant.room().board()[2][3]);
        assertNull(enPassant.room().board()[3][3]);
        assertTrue(enPassant.room().moveHistory().get(enPassant.room().moveHistory().size() - 1).contains("qua duong"));
        assertEquals("bP", enPassant.room().lastMove().capturedPiece());
    }

    @Test
    void repeatedPositionThreeTimesShouldEndAsDraw() {
        ChessOnlineRoomService service = new ChessOnlineRoomService();
        service.joinRoom("CHESS-THREEFOLD", "whiteUser", "White", "");
        service.joinRoom("CHESS-THREEFOLD", "blackUser", "Black", "");

        for (int i = 0; i < 2; i++) {
            assertTrue(service.move("CHESS-THREEFOLD", "whiteUser", 7, 6, 5, 5, null).ok()); // Ng1-f3
            assertTrue(service.move("CHESS-THREEFOLD", "blackUser", 0, 6, 2, 5, null).ok()); // Ng8-f6
            assertTrue(service.move("CHESS-THREEFOLD", "whiteUser", 5, 5, 7, 6, null).ok()); // Nf3-g1
            ChessOnlineRoomService.ActionResult repeat = service.move("CHESS-THREEFOLD", "blackUser", 2, 5, 0, 6, null); // Nf6-g8
            if (i == 1) {
                assertTrue(repeat.ok());
                assertNotNull(repeat.room());
                assertEquals("GAME_OVER", repeat.room().status());
                assertNull(repeat.room().currentTurnUserId());
                assertNull(repeat.room().getWinnerId());
                assertTrue(repeat.room().statusMessage().contains("lap lai vi tri 3 lan"));
            } else {
                assertTrue(repeat.ok());
                assertEquals("PLAYING", repeat.room().status());
            }
        }
    }

    @Test
    void fiftyMoveRuleShouldEndGameWhenHalfmoveClockReachesHundred() {
        ChessOnlineRoomService service = new ChessOnlineRoomService();
        service.joinRoom("CHESS-50MOVE", "whiteUser", "White", "");
        service.joinRoom("CHESS-50MOVE", "blackUser", "Black", "");

        @SuppressWarnings("unchecked")
        Map<String, Object> rooms = (Map<String, Object>) ReflectionTestUtils.getField(service, "rooms");
        assertNotNull(rooms);
        Object roomState = rooms.get("CHESS-50MOVE");
        assertNotNull(roomState);
        ReflectionTestUtils.setField(roomState, "halfmoveClock", 99);

        ChessOnlineRoomService.ActionResult draw = service.move("CHESS-50MOVE", "whiteUser", 7, 6, 5, 5, null);

        assertTrue(draw.ok());
        assertNotNull(draw.room());
        assertEquals("GAME_OVER", draw.room().status());
        assertNull(draw.room().getWinnerId());
        assertTrue(draw.room().statusMessage().contains("50 nuoc"));
    }
}
