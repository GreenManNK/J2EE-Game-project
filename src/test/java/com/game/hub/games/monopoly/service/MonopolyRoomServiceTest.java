package com.game.hub.games.monopoly.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonopolyRoomServiceTest {

    @Test
    void createRoomShouldAssignHostAndDefaultSettings() {
        MonopolyRoomService service = new MonopolyRoomService(new Random(7));

        MonopolyRoomService.RoomActionResult result = service.createRoom(
            new MonopolyRoomService.CreateRoomCommand(null, "Alice", "Phong Alpha", null, null, null)
        );

        assertTrue(result.success());
        assertNotNull(result.playerId());
        assertEquals(MonopolyRoomService.RoomStatus.WAITING, result.room().status());
        assertEquals(MonopolyRoomService.DEFAULT_STARTING_CASH, result.room().startingCash());
        assertEquals(MonopolyRoomService.DEFAULT_PASS_GO_AMOUNT, result.room().passGoAmount());
        assertEquals(1, result.room().players().size());
        assertTrue(result.room().players().get(0).host());
        assertEquals(result.playerId(), result.room().hostPlayerId());
    }

    @Test
    void selectTokenShouldRejectDuplicateChoice() {
        MonopolyRoomService service = new MonopolyRoomService(new Random(11));
        MonopolyRoomService.RoomActionResult created = service.createRoom(
            new MonopolyRoomService.CreateRoomCommand(null, "Alice", "Phong Dog", 4, 1500, 200)
        );
        String roomId = created.room().roomId();
        String hostId = created.playerId();

        MonopolyRoomService.RoomActionResult joined = service.joinRoom(
            roomId,
            new MonopolyRoomService.JoinRoomCommand(null, "Bob")
        );

        MonopolyRoomService.RoomActionResult hostToken = service.selectToken(
            roomId,
            new MonopolyRoomService.TokenSelectionCommand(hostId, "dog")
        );
        MonopolyRoomService.RoomActionResult duplicateToken = service.selectToken(
            roomId,
            new MonopolyRoomService.TokenSelectionCommand(joined.playerId(), "dog")
        );

        assertTrue(hostToken.success());
        assertFalse(duplicateToken.success());
        assertEquals("Token nay da co nguoi chon", duplicateToken.error());
    }

    @Test
    void startRoomShouldRequireHostAndTokens() {
        MonopolyRoomService service = new MonopolyRoomService(new Random(19));
        MonopolyRoomService.RoomActionResult created = service.createRoom(
            new MonopolyRoomService.CreateRoomCommand(null, "Alice", "Phong Bat Dau", 4, 1800, 250)
        );
        String roomId = created.room().roomId();
        String hostId = created.playerId();
        MonopolyRoomService.RoomActionResult joined = service.joinRoom(
            roomId,
            new MonopolyRoomService.JoinRoomCommand(null, "Bob")
        );

        MonopolyRoomService.RoomActionResult beforeToken = service.startRoom(
            roomId,
            new MonopolyRoomService.StartRoomCommand(hostId)
        );
        service.selectToken(roomId, new MonopolyRoomService.TokenSelectionCommand(hostId, "dog"));
        service.selectToken(roomId, new MonopolyRoomService.TokenSelectionCommand(joined.playerId(), "car"));
        MonopolyRoomService.RoomActionResult nonHost = service.startRoom(
            roomId,
            new MonopolyRoomService.StartRoomCommand(joined.playerId())
        );
        MonopolyRoomService.RoomActionResult started = service.startRoom(
            roomId,
            new MonopolyRoomService.StartRoomCommand(hostId)
        );

        assertFalse(beforeToken.success());
        assertEquals("Tat ca nguoi choi phai chon token truoc khi bat dau", beforeToken.error());
        assertFalse(nonHost.success());
        assertEquals("Chi host moi duoc bat dau game", nonHost.error());
        assertTrue(started.success());
        assertEquals(MonopolyRoomService.RoomStatus.PLAYING, started.room().status());
        assertEquals(1, started.room().version());
        assertTrue(started.room().hasGameState());
        assertNotNull(started.room().gameState());
        assertEquals("await_roll", started.room().gameState().get("phase"));
    }

    @Test
    void performActionShouldApplyServerStateAndRejectWrongTurn() {
        MonopolyRoomService service = new MonopolyRoomService(new Random(29));
        MonopolyRoomService.RoomActionResult created = service.createRoom(
            new MonopolyRoomService.CreateRoomCommand(null, "Alice", "Phong Action", 4, 1500, 200)
        );
        String roomId = created.room().roomId();
        String hostId = created.playerId();
        MonopolyRoomService.RoomActionResult joined = service.joinRoom(
            roomId,
            new MonopolyRoomService.JoinRoomCommand(null, "Bob")
        );
        String secondPlayerId = joined.playerId();

        service.selectToken(roomId, new MonopolyRoomService.TokenSelectionCommand(hostId, "dog"));
        service.selectToken(roomId, new MonopolyRoomService.TokenSelectionCommand(secondPlayerId, "car"));
        service.startRoom(roomId, new MonopolyRoomService.StartRoomCommand(hostId));

        MonopolyRoomService.RoomActionResult rollResult = service.performAction(
            roomId,
            new MonopolyRoomService.RoomGameActionCommand(hostId, "roll")
        );
        MonopolyRoomService.RoomActionResult wrongTurn = service.performAction(
            roomId,
            new MonopolyRoomService.RoomGameActionCommand(secondPlayerId, "roll")
        );

        assertTrue(rollResult.success());
        assertEquals(2, rollResult.room().version());
        assertNotNull(rollResult.room().gameState());
        assertTrue(rollResult.room().gameState().get("lastDice") instanceof Map<?, ?>);
        assertFalse(wrongTurn.success());
        assertEquals("Chua den luot cua ban", wrongTurn.error());
    }

    @Test
    void syncGameStateShouldFollowCurrentTurnOwner() {
        MonopolyRoomService service = new MonopolyRoomService(new Random(23));
        MonopolyRoomService.RoomActionResult created = service.createRoom(
            new MonopolyRoomService.CreateRoomCommand(null, "Alice", "Phong Dong Bo", 4, 1500, 200)
        );
        String roomId = created.room().roomId();
        String hostId = created.playerId();
        MonopolyRoomService.RoomActionResult joined = service.joinRoom(
            roomId,
            new MonopolyRoomService.JoinRoomCommand(null, "Bob")
        );
        String secondPlayerId = joined.playerId();

        service.selectToken(roomId, new MonopolyRoomService.TokenSelectionCommand(hostId, "dog"));
        service.selectToken(roomId, new MonopolyRoomService.TokenSelectionCommand(secondPlayerId, "car"));
        service.startRoom(roomId, new MonopolyRoomService.StartRoomCommand(hostId));

        Map<String, Object> initialState = Map.of(
            "phase", "await_roll",
            "currentPlayerIndex", 0,
            "players", List.of(
                Map.of("id", hostId, "name", "Alice"),
                Map.of("id", secondPlayerId, "name", "Bob")
            )
        );

        MonopolyRoomService.RoomActionResult firstSync = service.syncGameState(
            roomId,
            new MonopolyRoomService.SyncGameStateCommand(hostId, 1, initialState)
        );
        MonopolyRoomService.RoomActionResult wrongTurnSync = service.syncGameState(
            roomId,
            new MonopolyRoomService.SyncGameStateCommand(secondPlayerId, 2, initialState)
        );

        assertTrue(firstSync.success());
        assertEquals(2, firstSync.room().version());
        assertFalse(wrongTurnSync.success());
        assertEquals("Chua den luot cua ban", wrongTurnSync.error());
    }
}
