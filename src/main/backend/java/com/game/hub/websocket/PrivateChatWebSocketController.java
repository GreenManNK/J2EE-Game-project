package com.game.hub.websocket;

import com.game.hub.service.PrivateChatService;
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

        String currentUserId = sessionUserId(headers);
        String friendId = trimToNull(message.getFriendId());
        String content = trimToNull(message.getContent());

        if (currentUserId == null) {
            sendUserError(privateChatService.roomKey(trimToNull(message.getCurrentUserId()), friendId), trimToNull(message.getCurrentUserId()), "Login required");
            return;
        }
        if (friendId == null || content == null) {
            sendUserError(privateChatService.roomKey(currentUserId, friendId), currentUserId, "Missing chat payload");
            return;
        }

        PrivateChatService.SendResult result = privateChatService.saveMessage(
            currentUserId,
            friendId,
            content,
            trimToNull(message.getClientMessageId())
        );
        if (!result.ok()) {
            sendUserError(result.roomKey(), result.userId(), result.error() == null ? "Cannot send message" : result.error());
            return;
        }

        broadcastPayload(result.payload());
        sendUserWarning(result.roomKey(), result.userId(), result.warning());
    }

    private String trimToNull(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    private String sessionUserId(SimpMessageHeaderAccessor headers) {
        if (headers == null) {
            return null;
        }
        if (headers.getUser() != null) {
            String principalName = trimToNull(headers.getUser().getName());
            if (principalName != null && !principalName.startsWith("guest-")) {
                return principalName;
            }
        }
        if (headers.getSessionAttributes() == null) {
            return null;
        }
        Object value = headers.getSessionAttributes().get("AUTH_USER_ID");
        return trimToNull(value == null ? null : String.valueOf(value));
    }

    private void broadcastPayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return;
        }
        Object roomKeyValue = payload.get("roomKey");
        String roomKey = trimToNull(roomKeyValue == null ? null : String.valueOf(roomKeyValue));
        if (roomKey == null) {
            return;
        }

        messagingTemplate.convertAndSend("/topic/private." + roomKey, payload);

        Object fromUserIdValue = payload.get("fromUserId");
        Object toUserIdValue = payload.get("toUserId");
        String fromUserId = trimToNull(fromUserIdValue == null ? null : String.valueOf(fromUserIdValue));
        String toUserId = trimToNull(toUserIdValue == null ? null : String.valueOf(toUserIdValue));
        if (fromUserId != null) {
            messagingTemplate.convertAndSendToUser(fromUserId, "/queue/private-chat", payload);
        }
        if (toUserId != null && !toUserId.equals(fromUserId)) {
            messagingTemplate.convertAndSendToUser(toUserId, "/queue/private-chat", payload);
        }
    }

    private void sendUserError(String roomKey, String userId, String error) {
        if (roomKey == null || userId == null) {
            return;
        }
        Map<String, Object> payload = Map.of(
            "type", "ERROR",
            "roomKey", roomKey,
            "userId", userId,
            "error", error == null ? "Cannot send message" : error
        );
        messagingTemplate.convertAndSendToUser(userId, "/queue/private-chat", payload);
    }

    private void sendUserWarning(String roomKey, String userId, String warning) {
        if (roomKey == null || userId == null || warning == null || warning.isBlank()) {
            return;
        }
        messagingTemplate.convertAndSendToUser(userId, "/queue/private-chat", Map.of(
            "type", "WARNING",
            "roomKey", roomKey,
            "userId", userId,
            "message", warning
        ));
    }
}
