package com.game.hub.controller;

import com.game.hub.service.PrivateChatService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
@RequestMapping("/chat")
public class ChatController {
    private final PrivateChatService privateChatService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatController(PrivateChatService privateChatService,
                          SimpMessagingTemplate messagingTemplate) {
        this.privateChatService = privateChatService;
        this.messagingTemplate = messagingTemplate;
    }

    @GetMapping("/private")
    public String privatePage(@RequestParam(required = false) String currentUserId,
                              @RequestParam String friendId,
                              HttpServletRequest request,
                              Model model) {
        model.addAllAttributes(privateChat(currentUserId, friendId, request));
        return "chat/private";
    }

    @ResponseBody
    @GetMapping("/private/api")
    public Map<String, Object> privateChat(@RequestParam(required = false) String currentUserId,
                                           @RequestParam String friendId,
                                           HttpServletRequest request) {
        String sessionUserId = sessionUserId(request);
        if (sessionUserId == null || sessionUserId.isBlank()) {
            return Map.of("success", false, "error", "Login required");
        }
        String resolvedCurrentUserId = normalizeCurrentUserId(currentUserId, sessionUserId);
        return privateChatService.buildChatBootstrap(resolvedCurrentUserId, friendId).toMap();
    }

    @ResponseBody
    @PostMapping("/private/api/send")
    public Map<String, Object> sendPrivateMessage(@RequestBody(required = false) SendPrivateChatRequest requestBody,
                                                  HttpServletRequest request) {
        String sessionUserId = sessionUserId(request);
        if (sessionUserId == null || sessionUserId.isBlank()) {
            return Map.of("success", false, "error", "Login required");
        }
        if (requestBody == null || trimToNull(requestBody.friendId()) == null) {
            return Map.of("success", false, "error", "Friend id is required");
        }

        PrivateChatService.SendResult result = privateChatService.saveMessage(
            sessionUserId,
            requestBody.friendId(),
            requestBody.content(),
            requestBody.clientMessageId()
        );
        if (!result.ok()) {
            return Map.of("success", false, "error", result.error() == null ? "Cannot send message" : result.error());
        }

        broadcastPayload(result.payload());
        return Map.of(
            "success", true,
            "payload", result.payload()
        );
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String sessionUserId(HttpServletRequest request) {
        HttpSession session = request == null ? null : request.getSession(false);
        return trimToNull(asString(session == null ? null : session.getAttribute("AUTH_USER_ID")));
    }

    private String normalizeCurrentUserId(String requestedCurrentUserId, String sessionUserId) {
        String requested = trimToNull(requestedCurrentUserId);
        if (requested == null || sessionUserId == null || sessionUserId.equals(requested)) {
            return sessionUserId;
        }
        return sessionUserId;
    }

    private void broadcastPayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return;
        }
        String roomKey = trimToNull(asString(payload.get("roomKey")));
        if (roomKey == null) {
            return;
        }

        messagingTemplate.convertAndSend("/topic/private." + roomKey, payload);

        String fromUserId = trimToNull(asString(payload.get("fromUserId")));
        String toUserId = trimToNull(asString(payload.get("toUserId")));
        if (fromUserId != null) {
            messagingTemplate.convertAndSendToUser(fromUserId, "/queue/private-chat", payload);
        }
        if (toUserId != null && !toUserId.equals(fromUserId)) {
            messagingTemplate.convertAndSendToUser(toUserId, "/queue/private-chat", payload);
        }
    }

    public record SendPrivateChatRequest(String friendId, String content, String clientMessageId) {
    }
}
