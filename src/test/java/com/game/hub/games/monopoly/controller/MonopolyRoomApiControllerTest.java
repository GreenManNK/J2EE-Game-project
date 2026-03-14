package com.game.hub.games.monopoly.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.hub.games.monopoly.service.MonopolyRoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MonopolyRoomApiControllerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        objectMapper.findAndRegisterModules();
        MonopolyRoomApiController controller = new MonopolyRoomApiController(new MonopolyRoomService());
        mockMvc = MockMvcBuilders
            .standaloneSetup(controller)
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .build();
    }

    @Test
    void createRoomShouldReturnRoomAndHostPlayer() throws Exception {
        mockMvc.perform(post("/api/games/monopoly/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "playerName", "Alice",
                    "roomName", "Phong Test",
                    "maxPlayers", 4,
                    "startingCash", 1600,
                    "passGoAmount", 250
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.playerId").isString())
            .andExpect(jsonPath("$.room.roomName").value("Phong Test"))
            .andExpect(jsonPath("$.room.players", hasSize(1)))
            .andExpect(jsonPath("$.room.status").value("WAITING"));
    }

    @Test
    void roomShouldReturnNotFoundForMissingId() throws Exception {
        mockMvc.perform(get("/api/games/monopoly/rooms/UNKNOWN"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Khong tim thay phong"));
    }

    @Test
    void startRoomShouldRejectWhenNotEnoughPlayers() throws Exception {
        String createResponse = mockMvc.perform(post("/api/games/monopoly/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "playerName", "Alice"
                ))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        Map<?, ?> payload = objectMapper.readValue(createResponse, Map.class);
        Map<?, ?> room = (Map<?, ?>) payload.get("room");
        String roomId = String.valueOf(room.get("roomId"));
        String playerId = String.valueOf(payload.get("playerId"));

        mockMvc.perform(post("/api/games/monopoly/rooms/{roomId}/start", roomId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "playerId", playerId
                ))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Can it nhat 2 nguoi choi de bat dau"));
    }

    @Test
    void actionEndpointShouldApplyServerSideRoll() throws Exception {
        StartedRoom room = createStartedRoom();

        mockMvc.perform(post("/api/games/monopoly/rooms/{roomId}/action", room.roomId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "playerId", room.hostId(),
                    "action", "roll"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.room.version").value(2))
            .andExpect(jsonPath("$.room.gameState.lastDice.total").isNumber());
    }

    @Test
    void actionEndpointShouldRejectWrongTurnPlayer() throws Exception {
        StartedRoom room = createStartedRoom();

        mockMvc.perform(post("/api/games/monopoly/rooms/{roomId}/action", room.roomId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "playerId", room.secondPlayerId(),
                    "action", "roll"
                ))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Chua den luot cua ban"));
    }

    @Test
    void actionEndpointShouldBuildHouseWhenTileIndexProvided() throws Exception {
        StartedRoom room = createStartedRoom();
        Map<String, Object> state = currentGameState(room.roomId());
        setPlayerMoney(state, 0, 1500);
        state.put("phase", "await_end_turn");
        setTileOwner(state, 1, room.hostId(), 0, false);
        setTileOwner(state, 3, room.hostId(), 0, false);

        mockMvc.perform(post("/api/games/monopoly/rooms/{roomId}/sync", room.roomId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "playerId", room.hostId(),
                    "baseVersion", 1,
                    "gameState", state
                ))))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/games/monopoly/rooms/{roomId}/action", room.roomId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "playerId", room.hostId(),
                    "action", "build",
                    "tileIndex", 1
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.room.gameState.board[1].houses").value(1))
            .andExpect(jsonPath("$.room.gameState.players[0].money").value(1450));
    }

    @Test
    void actionEndpointShouldAcceptAuctionBidAmount() throws Exception {
        StartedRoom room = createStartedRoom();
        Map<String, Object> state = currentGameState(room.roomId());
        state.put("phase", "await_purchase");
        state.put("pendingPurchase", Map.of("tileIndex", 5));
        setPlayerMoney(state, 0, 1500);
        setPlayerMoney(state, 1, 1500);

        mockMvc.perform(post("/api/games/monopoly/rooms/{roomId}/sync", room.roomId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "playerId", room.hostId(),
                    "baseVersion", 1,
                    "gameState", state
                ))))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/games/monopoly/rooms/{roomId}/action", room.roomId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "playerId", room.hostId(),
                    "action", "skip_purchase"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.room.gameState.phase").value("auction"));

        mockMvc.perform(post("/api/games/monopoly/rooms/{roomId}/action", room.roomId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "playerId", room.hostId(),
                    "action", "auction_bid",
                    "amount", 100
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.room.gameState.auction.currentBid").value(100))
            .andExpect(jsonPath("$.room.gameState.auction.activePlayerId").value(room.secondPlayerId()));
    }

    @Test
    void actionEndpointShouldCreateTradeOffer() throws Exception {
        StartedRoom room = createStartedRoom();
        Map<String, Object> state = currentGameState(room.roomId());
        state.put("phase", "await_end_turn");
        setPlayerMoney(state, 0, 1500);
        setPlayerMoney(state, 1, 1500);
        setTileOwner(state, 1, room.hostId(), 0, false);
        setTileOwner(state, 6, room.secondPlayerId(), 0, false);

        mockMvc.perform(post("/api/games/monopoly/rooms/{roomId}/sync", room.roomId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "playerId", room.hostId(),
                    "baseVersion", 1,
                    "gameState", state
                ))))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/games/monopoly/rooms/{roomId}/action", room.roomId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "playerId", room.hostId(),
                    "action", "trade_offer",
                    "targetPlayerId", room.secondPlayerId(),
                    "offeredCash", 100,
                    "requestedCash", 50,
                    "offeredTileIndices", List.of(1),
                    "requestedTileIndices", List.of(6)
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.room.gameState.phase").value("trade"))
            .andExpect(jsonPath("$.room.gameState.tradeOffer.toPlayerId").value(room.secondPlayerId()))
            .andExpect(jsonPath("$.room.gameState.tradeOffer.offeredCash").value(100))
            .andExpect(jsonPath("$.room.gameState.tradeOffer.offeredTileIndices[0]").value(1));
    }

    @Test
    void leaveAndJoinShouldToggleDisconnectedPresenceDuringGame() throws Exception {
        StartedRoom room = createStartedRoom();

        mockMvc.perform(post("/api/games/monopoly/rooms/{roomId}/leave", room.roomId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "playerId", room.secondPlayerId()
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.room.status").value("PLAYING"))
            .andExpect(jsonPath("$.room.version").value(2))
            .andExpect(jsonPath("$.room.players[1].disconnected").value(true))
            .andExpect(jsonPath("$.room.gameState.players[1].isDisconnected").value(true));

        mockMvc.perform(post("/api/games/monopoly/rooms/{roomId}/join", room.roomId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "playerId", room.secondPlayerId(),
                    "playerName", "Bob Return"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.playerId").value(room.secondPlayerId()))
            .andExpect(jsonPath("$.room.version").value(3))
            .andExpect(jsonPath("$.room.players[1].name").value("Bob Return"))
            .andExpect(jsonPath("$.room.players[1].disconnected").value(false))
            .andExpect(jsonPath("$.room.gameState.players[1].isDisconnected").value(false));
    }

    private StartedRoom createStartedRoom() throws Exception {
        String createResponse = mockMvc.perform(post("/api/games/monopoly/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "playerName", "Alice",
                    "roomName", "Phong Action",
                    "maxPlayers", 4
                ))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        Map<?, ?> createPayload = objectMapper.readValue(createResponse, Map.class);
        Map<?, ?> room = (Map<?, ?>) createPayload.get("room");
        String roomId = String.valueOf(room.get("roomId"));
        String hostId = String.valueOf(createPayload.get("playerId"));

        String joinResponse = mockMvc.perform(post("/api/games/monopoly/rooms/{roomId}/join", roomId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "playerName", "Bob"
                ))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        Map<?, ?> joinPayload = objectMapper.readValue(joinResponse, Map.class);
        String secondPlayerId = String.valueOf(joinPayload.get("playerId"));

        mockMvc.perform(post("/api/games/monopoly/rooms/{roomId}/token", roomId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "playerId", hostId,
                    "token", "dog"
                ))))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/games/monopoly/rooms/{roomId}/token", roomId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "playerId", secondPlayerId,
                    "token", "car"
                ))))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/games/monopoly/rooms/{roomId}/start", roomId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "playerId", hostId
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.room.hasGameState").value(true));

        return new StartedRoom(roomId, hostId, secondPlayerId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> currentGameState(String roomId) throws Exception {
        String response = mockMvc.perform(get("/api/games/monopoly/rooms/{roomId}", roomId))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        Map<String, Object> payload = objectMapper.readValue(response, Map.class);
        Map<String, Object> room = (Map<String, Object>) payload.get("room");
        return (Map<String, Object>) room.get("gameState");
    }

    @SuppressWarnings("unchecked")
    private void setPlayerMoney(Map<String, Object> state, int playerIndex, int amount) {
        ((List<Map<String, Object>>) state.get("players")).get(playerIndex).put("money", amount);
    }

    @SuppressWarnings("unchecked")
    private void setTileOwner(Map<String, Object> state, int tileIndex, String ownerId, int houses, boolean mortgaged) {
        Map<String, Object> tile = ((List<Map<String, Object>>) state.get("board")).get(tileIndex);
        tile.put("ownerId", ownerId);
        tile.put("houses", houses);
        tile.put("mortgaged", mortgaged);
    }

    private record StartedRoom(String roomId, String hostId, String secondPlayerId) {
    }
}
