package com.game.hub.games.monopoly.controller;

import com.game.hub.games.monopoly.service.MonopolyRoomService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/games/monopoly/rooms")
public class MonopolyRoomApiController {
    private final MonopolyRoomService monopolyRoomService;

    public MonopolyRoomApiController(MonopolyRoomService monopolyRoomService) {
        this.monopolyRoomService = monopolyRoomService;
    }

    @GetMapping
    public Map<String, Object> listRooms() {
        return Map.of(
            "success", true,
            "rooms", monopolyRoomService.availableRooms()
        );
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<Map<String, Object>> room(@PathVariable String roomId) {
        MonopolyRoomService.RoomSnapshot room = monopolyRoomService.roomSnapshot(roomId);
        if (room == null) {
            return error(HttpStatus.NOT_FOUND, "Khong tim thay phong", null);
        }
        return ok(room, null);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createRoom(@RequestBody(required = false) CreateRoomRequest request) {
        CreateRoomRequest payload = request == null ? new CreateRoomRequest(null, null, null, null, null, null) : request;
        MonopolyRoomService.RoomActionResult result = monopolyRoomService.createRoom(
            new MonopolyRoomService.CreateRoomCommand(
                payload.playerId(),
                payload.playerName(),
                payload.roomName(),
                payload.maxPlayers(),
                payload.startingCash(),
                payload.passGoAmount()
            )
        );
        return ok(result.room(), result.playerId());
    }

    @PostMapping("/{roomId}/join")
    public ResponseEntity<Map<String, Object>> joinRoom(@PathVariable String roomId,
                                                        @RequestBody(required = false) JoinRoomRequest request) {
        JoinRoomRequest payload = request == null ? new JoinRoomRequest(null, null) : request;
        MonopolyRoomService.RoomActionResult result = monopolyRoomService.joinRoom(
            roomId,
            new MonopolyRoomService.JoinRoomCommand(payload.playerId(), payload.playerName())
        );
        return toResponse(result);
    }

    @PostMapping("/{roomId}/token")
    public ResponseEntity<Map<String, Object>> selectToken(@PathVariable String roomId,
                                                           @RequestBody(required = false) TokenSelectionRequest request) {
        TokenSelectionRequest payload = request == null ? new TokenSelectionRequest(null, null) : request;
        MonopolyRoomService.RoomActionResult result = monopolyRoomService.selectToken(
            roomId,
            new MonopolyRoomService.TokenSelectionCommand(payload.playerId(), payload.token())
        );
        return toResponse(result);
    }

    @PostMapping("/{roomId}/start")
    public ResponseEntity<Map<String, Object>> startRoom(@PathVariable String roomId,
                                                         @RequestBody(required = false) StartRoomRequest request) {
        StartRoomRequest payload = request == null ? new StartRoomRequest(null) : request;
        MonopolyRoomService.RoomActionResult result = monopolyRoomService.startRoom(
            roomId,
            new MonopolyRoomService.StartRoomCommand(payload.playerId())
        );
        return toResponse(result);
    }

    @PostMapping("/{roomId}/leave")
    public ResponseEntity<Map<String, Object>> leaveRoom(@PathVariable String roomId,
                                                         @RequestBody(required = false) LeaveRoomRequest request) {
        LeaveRoomRequest payload = request == null ? new LeaveRoomRequest(null) : request;
        MonopolyRoomService.RoomActionResult result = monopolyRoomService.leaveRoom(
            roomId,
            new MonopolyRoomService.LeaveRoomCommand(payload.playerId())
        );
        return toResponse(result);
    }

    @PostMapping("/{roomId}/sync")
    public ResponseEntity<Map<String, Object>> syncRoom(@PathVariable String roomId,
                                                        @RequestBody(required = false) SyncRoomRequest request) {
        SyncRoomRequest payload = request == null ? new SyncRoomRequest(null, 0, null) : request;
        MonopolyRoomService.RoomActionResult result = monopolyRoomService.syncGameState(
            roomId,
            new MonopolyRoomService.SyncGameStateCommand(payload.playerId(), payload.baseVersion(), payload.gameState())
        );
        return toResponse(result);
    }

    @PostMapping("/{roomId}/action")
    public ResponseEntity<Map<String, Object>> performAction(@PathVariable String roomId,
                                                             @RequestBody(required = false) RoomActionRequest request) {
        RoomActionRequest payload = request == null ? new RoomActionRequest(null, null) : request;
        MonopolyRoomService.RoomActionResult result = monopolyRoomService.performAction(
            roomId,
            new MonopolyRoomService.RoomGameActionCommand(payload.playerId(), payload.action())
        );
        return toResponse(result);
    }

    private ResponseEntity<Map<String, Object>> toResponse(MonopolyRoomService.RoomActionResult result) {
        if (result.success()) {
            return ok(result.room(), result.playerId());
        }
        if ("Khong tim thay phong".equals(result.error())) {
            return error(HttpStatus.NOT_FOUND, result.error(), result.room());
        }
        return error(HttpStatus.BAD_REQUEST, result.error(), result.room());
    }

    private ResponseEntity<Map<String, Object>> ok(MonopolyRoomService.RoomSnapshot room, String playerId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("room", room);
        if (playerId != null && !playerId.isBlank()) {
            body.put("playerId", playerId);
        }
        return ResponseEntity.ok(body);
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status,
                                                      String error,
                                                      MonopolyRoomService.RoomSnapshot room) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", error);
        if (room != null) {
            body.put("room", room);
        }
        return ResponseEntity.status(status).body(body);
    }

    public record CreateRoomRequest(
        String playerId,
        String playerName,
        String roomName,
        Integer maxPlayers,
        Integer startingCash,
        Integer passGoAmount
    ) {
    }

    public record JoinRoomRequest(String playerId, String playerName) {
    }

    public record TokenSelectionRequest(String playerId, String token) {
    }

    public record StartRoomRequest(String playerId) {
    }

    public record LeaveRoomRequest(String playerId) {
    }

    public record SyncRoomRequest(String playerId, int baseVersion, Map<String, Object> gameState) {
    }

    public record RoomActionRequest(String playerId, String action) {
    }
}
