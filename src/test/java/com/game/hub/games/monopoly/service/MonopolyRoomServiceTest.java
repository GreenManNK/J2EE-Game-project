package com.game.hub.games.monopoly.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
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
    void leaveRoomShouldMarkDisconnectedDuringActiveGameAndAllowReconnect() {
        MonopolyRoomService service = new MonopolyRoomService(new Random(21));
        StartedRoom room = createStartedRoom(service);

        MonopolyRoomService.RoomActionResult left = service.leaveRoom(
            room.roomId(),
            new MonopolyRoomService.LeaveRoomCommand(room.secondPlayerId())
        );
        MonopolyRoomService.RoomActionResult rejoined = service.joinRoom(
            room.roomId(),
            new MonopolyRoomService.JoinRoomCommand(room.secondPlayerId(), "Bob Return")
        );

        assertTrue(left.success());
        assertEquals(MonopolyRoomService.RoomStatus.PLAYING, left.room().status());
        assertEquals(2, left.room().version());
        assertTrue(left.room().players().stream()
            .filter(player -> room.secondPlayerId().equals(player.playerId()))
            .findFirst()
            .orElseThrow()
            .disconnected());
        assertTrue(Boolean.TRUE.equals(((List<Map<String, Object>>) left.room().gameState().get("players")).get(1).get("isDisconnected")));

        assertTrue(rejoined.success());
        assertEquals(room.secondPlayerId(), rejoined.playerId());
        assertEquals(3, rejoined.room().version());
        MonopolyRoomService.PlayerSnapshot rejoinedPlayer = rejoined.room().players().stream()
            .filter(player -> room.secondPlayerId().equals(player.playerId()))
            .findFirst()
            .orElseThrow();
        assertEquals("Bob Return", rejoinedPlayer.name());
        assertFalse(rejoinedPlayer.disconnected());
        assertFalse(Boolean.TRUE.equals(((List<Map<String, Object>>) rejoined.room().gameState().get("players")).get(1).get("isDisconnected")));
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
            new MonopolyRoomService.RoomGameActionCommand(hostId, "roll", null, null, null, null, null, null, null)
        );
        MonopolyRoomService.RoomActionResult wrongTurn = service.performAction(
            roomId,
            new MonopolyRoomService.RoomGameActionCommand(secondPlayerId, "roll", null, null, null, null, null, null, null)
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

    @Test
    void performActionShouldBuildHouseForOwnedColorSet() {
        MonopolyRoomService service = new MonopolyRoomService(new Random(31));
        StartedRoom room = createStartedRoom(service);

        Map<String, Object> state = mutableState(service, room.roomId());
        setCurrentPlayerMoney(state, 0, 1500);
        state.put("phase", "await_end_turn");
        setTileOwner(state, 1, room.hostId(), 0, false);
        setTileOwner(state, 3, room.hostId(), 0, false);

        MonopolyRoomService.RoomActionResult synced = service.syncGameState(
            room.roomId(),
            new MonopolyRoomService.SyncGameStateCommand(room.hostId(), 1, state)
        );
        MonopolyRoomService.RoomActionResult build = service.performAction(
            room.roomId(),
            new MonopolyRoomService.RoomGameActionCommand(room.hostId(), "build", 1, null, null, null, null, null, null)
        );

        assertTrue(synced.success());
        assertTrue(build.success());
        assertEquals(3, build.room().version());
        assertEquals(1450, playerMoney(build.room().gameState(), 0));
        assertEquals(1, tileHouses(build.room().gameState(), 1));
    }

    @Test
    void performActionShouldAllowMortgageToSettleDebt() {
        MonopolyRoomService service = new MonopolyRoomService(new Random(37));
        StartedRoom room = createStartedRoom(service);

        Map<String, Object> state = mutableState(service, room.roomId());
        setCurrentPlayerMoney(state, 0, 10);
        state.put("phase", "debt");
        state.put("debt", new LinkedHashMap<>(Map.of(
            "playerId", room.hostId(),
            "creditorId", room.secondPlayerId(),
            "amount", 50,
            "reason", "Tien thue thu nghiem",
            "toPot", false
        )));
        setTileOwner(state, 5, room.hostId(), 0, false);

        MonopolyRoomService.RoomActionResult synced = service.syncGameState(
            room.roomId(),
            new MonopolyRoomService.SyncGameStateCommand(room.hostId(), 1, state)
        );
        MonopolyRoomService.RoomActionResult mortgage = service.performAction(
            room.roomId(),
            new MonopolyRoomService.RoomGameActionCommand(room.hostId(), "mortgage", 5, null, null, null, null, null, null)
        );

        assertTrue(synced.success());
        assertTrue(mortgage.success());
        assertEquals(60, playerMoney(mortgage.room().gameState(), 0));
        assertEquals(1550, playerMoney(mortgage.room().gameState(), 1));
        assertTrue(tileMortgaged(mortgage.room().gameState(), 5));
        assertFalse(mortgage.room().gameState().containsKey("debt") && mortgage.room().gameState().get("debt") != null);
        assertEquals("await_end_turn", mortgage.room().gameState().get("phase"));
    }

    @Test
    void performActionShouldRunAuctionAfterSkipPurchase() {
        MonopolyRoomService service = new MonopolyRoomService(new Random(41));
        StartedRoom room = createStartedRoom(service);

        Map<String, Object> state = mutableState(service, room.roomId());
        state.put("phase", "await_purchase");
        state.put("pendingPurchase", new LinkedHashMap<>(Map.of("tileIndex", 5)));
        setCurrentPlayerMoney(state, 0, 1500);
        setCurrentPlayerMoney(state, 1, 1500);

        MonopolyRoomService.RoomActionResult synced = service.syncGameState(
            room.roomId(),
            new MonopolyRoomService.SyncGameStateCommand(room.hostId(), 1, state)
        );
        MonopolyRoomService.RoomActionResult skipPurchase = service.performAction(
            room.roomId(),
            new MonopolyRoomService.RoomGameActionCommand(room.hostId(), "skip_purchase", null, null, null, null, null, null, null)
        );
        MonopolyRoomService.RoomActionResult hostBid = service.performAction(
            room.roomId(),
            new MonopolyRoomService.RoomGameActionCommand(room.hostId(), "auction_bid", null, 100, null, null, null, null, null)
        );
        MonopolyRoomService.RoomActionResult secondPass = service.performAction(
            room.roomId(),
            new MonopolyRoomService.RoomGameActionCommand(room.secondPlayerId(), "auction_pass", null, null, null, null, null, null, null)
        );

        assertTrue(synced.success());
        assertTrue(skipPurchase.success());
        assertEquals("auction", skipPurchase.room().gameState().get("phase"));
        assertEquals(room.hostId(), ((Map<?, ?>) skipPurchase.room().gameState().get("auction")).get("activePlayerId"));

        assertTrue(hostBid.success());
        assertEquals(100, ((Number) ((Map<?, ?>) hostBid.room().gameState().get("auction")).get("currentBid")).intValue());
        assertEquals(room.secondPlayerId(), ((Map<?, ?>) hostBid.room().gameState().get("auction")).get("activePlayerId"));

        assertTrue(secondPass.success());
        assertEquals("await_end_turn", secondPass.room().gameState().get("phase"));
        assertEquals(room.hostId(), ((List<Map<String, Object>>) secondPass.room().gameState().get("board")).get(5).get("ownerId"));
        assertEquals(1400, playerMoney(secondPass.room().gameState(), 0));
    }

    @Test
    void performActionShouldAcceptTradeAndSwapAssetsAndCash() {
        MonopolyRoomService service = new MonopolyRoomService(new Random(43));
        StartedRoom room = createStartedRoom(service);

        Map<String, Object> state = mutableState(service, room.roomId());
        state.put("phase", "await_end_turn");
        setCurrentPlayerMoney(state, 0, 1500);
        setCurrentPlayerMoney(state, 1, 1500);
        setTileOwner(state, 1, room.hostId(), 0, false);
        setTileOwner(state, 6, room.secondPlayerId(), 0, false);

        MonopolyRoomService.RoomActionResult synced = service.syncGameState(
            room.roomId(),
            new MonopolyRoomService.SyncGameStateCommand(room.hostId(), 1, state)
        );
        MonopolyRoomService.RoomActionResult offer = service.performAction(
            room.roomId(),
            new MonopolyRoomService.RoomGameActionCommand(
                room.hostId(),
                "trade_offer",
                null,
                null,
                room.secondPlayerId(),
                100,
                50,
                List.of(1),
                List.of(6)
            )
        );
        MonopolyRoomService.RoomActionResult accept = service.performAction(
            room.roomId(),
            new MonopolyRoomService.RoomGameActionCommand(
                room.secondPlayerId(),
                "trade_accept",
                null,
                null,
                null,
                null,
                null,
                null,
                null
            )
        );

        assertTrue(synced.success());
        assertTrue(offer.success());
        assertEquals("trade", offer.room().gameState().get("phase"));
        assertEquals(room.secondPlayerId(), ((Map<?, ?>) offer.room().gameState().get("tradeOffer")).get("toPlayerId"));

        assertTrue(accept.success());
        assertEquals("await_end_turn", accept.room().gameState().get("phase"));
        assertFalse(accept.room().gameState().containsKey("tradeOffer") && accept.room().gameState().get("tradeOffer") != null);
        assertEquals(1450, playerMoney(accept.room().gameState(), 0));
        assertEquals(1550, playerMoney(accept.room().gameState(), 1));
        assertEquals(room.secondPlayerId(), ((List<Map<String, Object>>) accept.room().gameState().get("board")).get(1).get("ownerId"));
        assertEquals(room.hostId(), ((List<Map<String, Object>>) accept.room().gameState().get("board")).get(6).get("ownerId"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mutableState(MonopolyRoomService service, String roomId) {
        return (Map<String, Object>) service.roomSnapshot(roomId).gameState();
    }

    @SuppressWarnings("unchecked")
    private void setCurrentPlayerMoney(Map<String, Object> state, int playerIndex, int money) {
        List<Map<String, Object>> players = (List<Map<String, Object>>) state.get("players");
        players.get(playerIndex).put("money", money);
    }

    @SuppressWarnings("unchecked")
    private void setTileOwner(Map<String, Object> state, int tileIndex, String ownerId, int houses, boolean mortgaged) {
        List<Map<String, Object>> board = (List<Map<String, Object>>) state.get("board");
        board.get(tileIndex).put("ownerId", ownerId);
        board.get(tileIndex).put("houses", houses);
        board.get(tileIndex).put("mortgaged", mortgaged);
    }

    @SuppressWarnings("unchecked")
    private int playerMoney(Map<String, Object> state, int playerIndex) {
        return ((Number) ((List<Map<String, Object>>) state.get("players")).get(playerIndex).get("money")).intValue();
    }

    @SuppressWarnings("unchecked")
    private int tileHouses(Map<String, Object> state, int tileIndex) {
        return ((Number) ((List<Map<String, Object>>) state.get("board")).get(tileIndex).get("houses")).intValue();
    }

    @SuppressWarnings("unchecked")
    private boolean tileMortgaged(Map<String, Object> state, int tileIndex) {
        return Boolean.TRUE.equals(((List<Map<String, Object>>) state.get("board")).get(tileIndex).get("mortgaged"));
    }

    private StartedRoom createStartedRoom(MonopolyRoomService service) {
        MonopolyRoomService.RoomActionResult created = service.createRoom(
            new MonopolyRoomService.CreateRoomCommand(null, "Alice", "Phong Co ty phu", 4, 1500, 200)
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
        return new StartedRoom(roomId, hostId, secondPlayerId);
    }

    private record StartedRoom(String roomId, String hostId, String secondPlayerId) {
    }
}
