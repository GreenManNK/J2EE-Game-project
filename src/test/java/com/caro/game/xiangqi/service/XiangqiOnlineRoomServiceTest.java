package com.caro.game.xiangqi.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XiangqiOnlineRoomServiceTest {

    @Test
    void joinRoomShouldAssignRedThenBlackAndRejectThirdPlayer() {
        XiangqiOnlineRoomService service = new XiangqiOnlineRoomService();

        XiangqiOnlineRoomService.JoinResult first = service.joinRoom("XQ-1", "u1", "User 1", "");
        XiangqiOnlineRoomService.JoinResult second = service.joinRoom("XQ-1", "u2", "User 2", "");
        XiangqiOnlineRoomService.JoinResult third = service.joinRoom("XQ-1", "u3", "User 3", "");

        assertTrue(first.ok());
        assertEquals("r", first.assignedColor());
        assertTrue(second.ok());
        assertEquals("b", second.assignedColor());
        assertFalse(third.ok());
        assertEquals("Room is full", third.error());
        assertNotNull(second.room());
        assertEquals(2, second.room().playerCount());
        assertEquals("r", second.room().currentTurnColor());
    }

    @Test
    void moveShouldEnforceTurnAndSwitchToOtherPlayer() {
        XiangqiOnlineRoomService service = new XiangqiOnlineRoomService();
        service.joinRoom("XQ-2", "redUser", "R", "");
        service.joinRoom("XQ-2", "blackUser", "B", "");

        XiangqiOnlineRoomService.ActionResult okMove = service.move("XQ-2", "redUser", 9, 0, 8, 0, null);

        assertTrue(okMove.ok());
        assertEquals("MOVE", okMove.eventType());
        assertNotNull(okMove.room());
        assertEquals("blackUser", okMove.room().currentTurnUserId());
        assertEquals("b", okMove.room().currentTurnColor());
        assertNull(okMove.room().board()[9][0]);
        assertEquals("rR", okMove.room().board()[8][0]);
        assertEquals(1, okMove.room().moveHistory().size());

        XiangqiOnlineRoomService.ActionResult wrongTurn = service.move("XQ-2", "redUser", 9, 1, 7, 2, null);
        assertFalse(wrongTurn.ok());
        assertEquals("Not your turn", wrongTurn.error());
    }

    @Test
    void moveShouldRejectIllegalPieceMovement() {
        XiangqiOnlineRoomService service = new XiangqiOnlineRoomService();
        service.joinRoom("XQ-ILLEGAL", "redUser", "R", "");
        service.joinRoom("XQ-ILLEGAL", "blackUser", "B", "");

        XiangqiOnlineRoomService.ActionResult result = service.move("XQ-ILLEGAL", "redUser", 9, 0, 8, 1, null);

        assertFalse(result.ok());
        assertEquals("Illegal move", result.error());
    }

    @Test
    void flyingGeneralCaptureShouldMarkGameOver() {
        XiangqiOnlineRoomService service = new XiangqiOnlineRoomService();
        service.joinRoom("XQ-MATE", "redUser", "Red", "");
        service.joinRoom("XQ-MATE", "blackUser", "Black", "");
        forceMinimalFlyingGeneralBoard(service, "XQ-MATE", "blackUser");

        XiangqiOnlineRoomService.ActionResult win = service.move("XQ-MATE", "blackUser", 0, 4, 9, 4, null); // flying general capture

        assertTrue(win.ok());
        assertNotNull(win.room());
        assertEquals("GAME_OVER", win.room().status());
        assertNull(win.room().currentTurnUserId());
        assertTrue(win.room().statusMessage().contains("an Tuong"));
    }

    @Test
    void leaveRoomShouldKeepRoomWaitingForNewOpponent() {
        XiangqiOnlineRoomService service = new XiangqiOnlineRoomService();
        service.joinRoom("XQ-3", "u2", "U2", "");
        service.joinRoom("XQ-3", "u1", "U1", "");

        service.leaveRoom("XQ-3", "u1");

        XiangqiOnlineRoomService.RoomSnapshot snapshot = service.roomSnapshot("XQ-3");
        assertNotNull(snapshot);
        assertEquals(1, snapshot.playerCount());
        assertEquals("WAITING", snapshot.status());
        assertNull(snapshot.currentTurnUserId());
        assertEquals(1, service.availableRooms().size());
        assertEquals("XQ-3", service.availableRooms().getFirst().roomId());
        assertEquals("r", snapshot.players().getFirst().color());
    }

    @Test
    void surrenderShouldMarkGameOverAndBlockFurtherMoves() {
        XiangqiOnlineRoomService service = new XiangqiOnlineRoomService();
        service.joinRoom("XQ-4", "redUser", "Red", "");
        service.joinRoom("XQ-4", "blackUser", "Black", "");

        XiangqiOnlineRoomService.ActionResult surrender = service.surrenderGame("XQ-4", "redUser");

        assertTrue(surrender.ok());
        assertEquals("SURRENDER", surrender.eventType());
        assertNotNull(surrender.room());
        assertEquals("GAME_OVER", surrender.room().status());
        assertNull(surrender.room().currentTurnUserId());
        assertTrue(surrender.room().statusMessage().contains("dau hang"));
        assertEquals("Do dau hang", surrender.room().moveHistory().getLast());

        XiangqiOnlineRoomService.ActionResult moveAfterSurrender = service.move("XQ-4", "blackUser", 0, 0, 1, 0, null);
        assertFalse(moveAfterSurrender.ok());
        assertEquals("Game already ended", moveAfterSurrender.error());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void forceMinimalFlyingGeneralBoard(XiangqiOnlineRoomService service,
                                                       String roomId,
                                                       String currentTurnUserId) {
        try {
            Field roomsField = XiangqiOnlineRoomService.class.getDeclaredField("rooms");
            roomsField.setAccessible(true);
            Map rooms = (Map) roomsField.get(service);
            Object roomState = rooms.get(roomId);
            if (roomState == null) {
                throw new IllegalStateException("Room not found: " + roomId);
            }

            String[][] board = new String[10][9];
            board[0][4] = "bG";
            board[9][4] = "rG";

            Field boardField = roomState.getClass().getDeclaredField("board");
            boardField.setAccessible(true);
            boardField.set(roomState, board);

            Field statusField = roomState.getClass().getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(roomState, "PLAYING");

            Field statusMessageField = roomState.getClass().getDeclaredField("statusMessage");
            statusMessageField.setAccessible(true);
            statusMessageField.set(roomState, "Test setup");

            Field currentTurnUserIdField = roomState.getClass().getDeclaredField("currentTurnUserId");
            currentTurnUserIdField.setAccessible(true);
            currentTurnUserIdField.set(roomState, currentTurnUserId);

            Field currentTurnColorField = roomState.getClass().getDeclaredField("currentTurnColor");
            currentTurnColorField.setAccessible(true);
            currentTurnColorField.set(roomState, "b");

            Field moveHistoryField = roomState.getClass().getDeclaredField("moveHistory");
            moveHistoryField.setAccessible(true);
            Object moveHistory = moveHistoryField.get(roomState);
            if (moveHistory instanceof List<?> list) {
                ((List) list).clear();
            }

            Field lastMoveField = roomState.getClass().getDeclaredField("lastMove");
            lastMoveField.setAccessible(true);
            lastMoveField.set(roomState, null);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }
}
