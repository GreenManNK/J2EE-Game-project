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

    private record StartedRoom(String roomId, String hostId, String secondPlayerId) {
    }
}
