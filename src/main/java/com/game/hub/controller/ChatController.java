package com.game.hub.controller;

import com.game.hub.service.PrivateChatService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
@RequestMapping("/chat")
public class ChatController {
    private final PrivateChatService privateChatService;

    public ChatController(PrivateChatService privateChatService) {
        this.privateChatService = privateChatService;
    }

    @GetMapping("/private")
    public String privatePage(@RequestParam String currentUserId,
                              @RequestParam String friendId,
                              HttpServletRequest request,
                              Model model) {
        model.addAllAttributes(privateChat(currentUserId, friendId, request));
        return "chat/private";
    }

    @ResponseBody
    @GetMapping("/private/api")
    public Map<String, Object> privateChat(@RequestParam String currentUserId,
                                           @RequestParam String friendId,
                                           HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String sessionUserId = session == null ? null : asString(session.getAttribute("AUTH_USER_ID"));
        if (sessionUserId == null || sessionUserId.isBlank()) {
            return Map.of("success", false, "error", "Login required");
        }
        if (!sessionUserId.equals(currentUserId)) {
            return Map.of("success", false, "error", "Session user mismatch");
        }
        return privateChatService.buildChatBootstrap(currentUserId, friendId).toMap();
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
