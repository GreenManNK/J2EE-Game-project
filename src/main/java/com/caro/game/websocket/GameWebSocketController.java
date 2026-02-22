package com.caro.game.websocket;

import com.caro.game.service.GameRoomService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.Map;

@Controller
public class GameWebSocketController {
    private final GameRoomService gameRoomService;
    private final SimpMessagingTemplate messagingTemplate;

    public GameWebSocketController(GameRoomService gameRoomService, SimpMessagingTemplate messagingTemplate) {
        this.gameRoomService = gameRoomService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/game.join")
    public void join(JoinGameMessage message) {
        GameRoomService.JoinResult result = gameRoomService.joinRoom(message.getRoomId(), message.getUserId());
        if (!result.ok()) {
            messagingTemplate.convertAndSendToUser(message.getUserId(), "/queue/errors", Map.of("error", result.error()));
            return;
        }

        messagingTemplate.convertAndSend("/topic/room." + message.getRoomId(), Map.of(
            "type", "PLAYER_JOINED",
            "userId", message.getUserId(),
            "symbol", result.symbol(),
            "displayName", message.getDisplayName(),
            "avatarPath", message.getAvatarPath()
        ));

        messagingTemplate.convertAndSend("/topic/lobby.rooms", Map.of(
            "type", "ROOM_LIST",
            "rooms", gameRoomService.availableRooms()
        ));
    }

    @MessageMapping("/game.move")
    public void move(MoveMessage message) {
        GameRoomService.MoveResult result = gameRoomService.makeMove(message.getRoomId(), message.getUserId(), message.getX(), message.getY());
        if (!result.ok()) {
            messagingTemplate.convertAndSendToUser(message.getUserId(), "/queue/errors", Map.of("error", result.error()));
            return;
        }

        Map<String, Object> movePayload = new HashMap<>();
        movePayload.put("type", "MOVE");
        movePayload.put("x", result.x());
        movePayload.put("y", result.y());
        movePayload.put("symbol", result.symbol());
        if (result.nextTurnUserId() != null) {
            movePayload.put("nextTurnUserId", result.nextTurnUserId());
        }
        messagingTemplate.convertAndSend("/topic/room." + message.getRoomId(), movePayload);

        if (result.win()) {
            Map<String, Object> gameOverPayload = new HashMap<>();
            gameOverPayload.put("type", "GAME_OVER");
            gameOverPayload.put("winnerUserId", result.winnerUserId());
            if (result.loserUserId() != null) {
                gameOverPayload.put("loserUserId", result.loserUserId());
            }
            if (result.winnerScore() != null) {
                gameOverPayload.put("winnerScore", result.winnerScore());
            }
            if (result.loserScore() != null) {
                gameOverPayload.put("loserScore", result.loserScore());
            }
            messagingTemplate.convertAndSend("/topic/room." + message.getRoomId(), gameOverPayload);
            gameRoomService.resetRoom(message.getRoomId());
        }
    }

    @MessageMapping("/game.leave")
    public void leave(LeaveGameMessage message) {
        gameRoomService.leaveRoom(message.getRoomId(), message.getUserId());
        messagingTemplate.convertAndSend("/topic/room." + message.getRoomId(), Map.of(
            "type", "PLAYER_LEFT",
            "userId", message.getUserId()
        ));
        messagingTemplate.convertAndSend("/topic/lobby.rooms", Map.of(
            "type", "ROOM_LIST",
            "rooms", gameRoomService.availableRooms()
        ));
    }

    @MessageMapping("/game.chat")
    public void chat(ChatMessage message) {
        messagingTemplate.convertAndSend("/topic/room." + message.getRoomId(), Map.of(
            "type", "CHAT",
            "userId", message.getUserId(),
            "displayName", message.getDisplayName(),
            "avatarPath", message.getAvatarPath(),
            "message", message.getContent()
        ));
    }
}
