package com.caro.game.websocket;

import com.caro.game.entity.UserAccount;
import com.caro.game.repository.UserAccountRepository;
import com.caro.game.service.GameRoomService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.Map;

@Controller
public class GameWebSocketController {
    private final GameRoomService gameRoomService;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserAccountRepository userAccountRepository;

    public GameWebSocketController(GameRoomService gameRoomService,
                                   SimpMessagingTemplate messagingTemplate,
                                   UserAccountRepository userAccountRepository) {
        this.gameRoomService = gameRoomService;
        this.messagingTemplate = messagingTemplate;
        this.userAccountRepository = userAccountRepository;
    }

    @MessageMapping("/game.join")
    public void join(JoinGameMessage message, SimpMessageHeaderAccessor headers) {
        if (message == null) {
            return;
        }
        String userId = requireSessionUser(message.getRoomId(), message.getUserId(), headers);
        if (userId == null) {
            return;
        }
        GameRoomService.JoinResult result = gameRoomService.joinRoom(message.getRoomId(), userId);
        if (!result.ok()) {
            sendUserError(message.getRoomId(), userId, result.error());
            return;
        }

        PlayerMeta playerMeta = playerMeta(userId);
        messagingTemplate.convertAndSend("/topic/room." + message.getRoomId(), Map.of(
            "type", "PLAYER_JOINED",
            "userId", userId,
            "symbol", result.symbol(),
            "currentTurnUserId", result.currentTurnUserId() == null ? "" : result.currentTurnUserId(),
            "playerCount", result.playerCount(),
            "board", gameRoomService.getBoardSnapshot(message.getRoomId()),
            "displayName", playerMeta.displayName(),
            "avatarPath", playerMeta.avatarPath()
        ));

        messagingTemplate.convertAndSend("/topic/lobby.rooms", Map.of(
            "type", "ROOM_LIST",
            "rooms", gameRoomService.availableRooms()
        ));
    }

    @MessageMapping("/game.move")
    public void move(MoveMessage message, SimpMessageHeaderAccessor headers) {
        if (message == null) {
            return;
        }
        String userId = requireSessionUser(message.getRoomId(), message.getUserId(), headers);
        if (userId == null) {
            return;
        }
        GameRoomService.MoveResult result = gameRoomService.makeMove(message.getRoomId(), userId, message.getX(), message.getY());
        if (!result.ok()) {
            sendUserError(message.getRoomId(), userId, result.error());
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
            messagingTemplate.convertAndSend("/topic/room." + message.getRoomId(), Map.of(
                "type", "RESET",
                "board", gameRoomService.getBoardSnapshot(message.getRoomId()),
                "currentTurnUserId", gameRoomService.getCurrentTurnUserId(message.getRoomId())
            ));
            return;
        }

        if (result.draw()) {
            messagingTemplate.convertAndSend("/topic/room." + message.getRoomId(), Map.of(
                "type", "DRAW"
            ));
            gameRoomService.resetRoom(message.getRoomId());
            messagingTemplate.convertAndSend("/topic/room." + message.getRoomId(), Map.of(
                "type", "RESET",
                "board", gameRoomService.getBoardSnapshot(message.getRoomId()),
                "currentTurnUserId", gameRoomService.getCurrentTurnUserId(message.getRoomId())
            ));
        }
    }

    @MessageMapping("/game.leave")
    public void leave(LeaveGameMessage message, SimpMessageHeaderAccessor headers) {
        if (message == null) {
            return;
        }
        String userId = requireSessionUser(message.getRoomId(), message.getUserId(), headers);
        if (userId == null) {
            return;
        }
        gameRoomService.leaveRoom(message.getRoomId(), userId);
        messagingTemplate.convertAndSend("/topic/room." + message.getRoomId(), Map.of(
            "type", "PLAYER_LEFT",
            "userId", userId
        ));
        messagingTemplate.convertAndSend("/topic/lobby.rooms", Map.of(
            "type", "ROOM_LIST",
            "rooms", gameRoomService.availableRooms()
        ));
    }

    @MessageMapping("/game.chat")
    public void chat(ChatMessage message, SimpMessageHeaderAccessor headers) {
        if (message == null || message.getRoomId() == null || message.getRoomId().isBlank()) {
            return;
        }
        String userId = requireSessionUser(message.getRoomId(), message.getUserId(), headers);
        if (userId == null) {
            return;
        }
        String text = message.getContent() == null ? "" : message.getContent().trim();
        if (text.isBlank()) {
            return;
        }
        PlayerMeta playerMeta = playerMeta(userId);
        messagingTemplate.convertAndSend("/topic/room." + message.getRoomId(), Map.of(
            "type", "CHAT",
            "userId", userId,
            "displayName", playerMeta.displayName(),
            "avatarPath", playerMeta.avatarPath(),
            "message", text
        ));
    }

    private void sendUserError(String roomId, String userId, String error) {
        if (userId != null && !userId.isBlank()) {
            messagingTemplate.convertAndSendToUser(userId, "/queue/errors", Map.of("error", error));
        }
        if (roomId != null && !roomId.isBlank()) {
            messagingTemplate.convertAndSend("/topic/room." + roomId, Map.of(
                "type", "ERROR",
                "userId", userId,
                "error", error
            ));
        }
    }

    private String requireSessionUser(String roomId,
                                      String claimedUserId,
                                      SimpMessageHeaderAccessor headers) {
        String sessionUserId = sessionUserId(headers);
        if (sessionUserId == null) {
            sendUserError(roomId, claimedUserId, "Login required");
            return null;
        }
        if (claimedUserId == null || claimedUserId.isBlank()) {
            sendUserError(roomId, sessionUserId, "UserId is required");
            return null;
        }
        if (!sessionUserId.equals(claimedUserId)) {
            sendUserError(roomId, sessionUserId, "Session user mismatch");
            return null;
        }
        return sessionUserId;
    }

    private String sessionUserId(SimpMessageHeaderAccessor headers) {
        if (headers == null || headers.getSessionAttributes() == null) {
            return null;
        }
        Object value = headers.getSessionAttributes().get("AUTH_USER_ID");
        if (value == null) {
            return null;
        }
        String userId = String.valueOf(value).trim();
        return userId.isEmpty() ? null : userId;
    }

    private PlayerMeta playerMeta(String userId) {
        UserAccount user = userAccountRepository.findById(userId).orElse(null);
        if (user == null) {
            return new PlayerMeta(userId, "");
        }
        String displayName = user.getDisplayName() == null || user.getDisplayName().isBlank()
            ? userId
            : user.getDisplayName();
        String avatarPath = user.getAvatarPath() == null ? "" : user.getAvatarPath();
        return new PlayerMeta(displayName, avatarPath);
    }

    private record PlayerMeta(String displayName, String avatarPath) {
    }
}
