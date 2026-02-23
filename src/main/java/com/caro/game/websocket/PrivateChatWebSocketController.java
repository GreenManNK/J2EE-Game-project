package com.caro.game.websocket;

import com.caro.game.service.PrivateChatService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
public class PrivateChatWebSocketController {
    private final SimpMessagingTemplate messagingTemplate;
    private final PrivateChatService privateChatService;

    public PrivateChatWebSocketController(SimpMessagingTemplate messagingTemplate,
                                          PrivateChatService privateChatService) {
        this.messagingTemplate = messagingTemplate;
        this.privateChatService = privateChatService;
    }

    @MessageMapping("/private-chat.send")
    public void send(PrivateChatMessage message, SimpMessageHeaderAccessor headers) {
        if (message == null) {
            return;
        }

        String currentUserId = trimToNull(message.getCurrentUserId());
        String friendId = trimToNull(message.getFriendId());
        String content = trimToNull(message.getContent());

        if (currentUserId == null || friendId == null || content == null) {
            return;
        }

        String sessionUserId = sessionUserId(headers);
        if (sessionUserId == null) {
            String roomKey = privateChatService.roomKey(currentUserId, friendId);
            if (roomKey != null) {
                messagingTemplate.convertAndSend("/topic/private." + roomKey, Map.of(
                    "type", "ERROR",
                    "userId", currentUserId,
                    "error", "Login required"
                ));
            }
            return;
        }

        if (!sessionUserId.equals(currentUserId)) {
            String roomKey = privateChatService.roomKey(currentUserId, friendId);
            if (roomKey != null) {
                messagingTemplate.convertAndSend("/topic/private." + roomKey, Map.of(
                    "type", "ERROR",
                    "userId", currentUserId,
                    "error", "Session user mismatch"
                ));
            }
            return;
        }

        PrivateChatService.SendResult result = privateChatService.saveMessage(currentUserId, friendId, content);
        if (!result.ok()) {
            if (result.roomKey() != null && result.userId() != null) {
                messagingTemplate.convertAndSend("/topic/private." + result.roomKey(), Map.of(
                    "type", "ERROR",
                    "userId", result.userId(),
                    "error", result.error() == null ? "Cannot send message" : result.error()
                ));
            }
            return;
        }

        messagingTemplate.convertAndSend("/topic/private." + result.roomKey(), result.payload());
    }

    private String trimToNull(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
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
}
