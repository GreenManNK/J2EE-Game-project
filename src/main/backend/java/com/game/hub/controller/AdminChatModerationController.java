package com.game.hub.controller;

import com.game.hub.entity.ChatModerationTerm;
import com.game.hub.service.ChatModerationTermService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/api/chat-moderation/terms")
public class AdminChatModerationController {
    private final ChatModerationTermService chatModerationTermService;

    public AdminChatModerationController(ChatModerationTermService chatModerationTermService) {
        this.chatModerationTermService = chatModerationTermService;
    }

    @ResponseBody
    @GetMapping
    public Object listTerms() {
        List<Map<String, Object>> terms = chatModerationTermService.listAdminTerms().stream()
            .map(this::toPayload)
            .toList();
        return Map.of(
            "success", true,
            "terms", terms
        );
    }

    @ResponseBody
    @PostMapping
    public Object createTerm(@RequestBody CreateTermRequest request) {
        try {
            ChatModerationTerm created = chatModerationTermService.addTerm(request == null ? null : request.term());
            return Map.of(
                "success", true,
                "term", toPayload(new ChatModerationTermService.ModerationTermView(created.getId(), created.getTerm(), "database", true))
            );
        } catch (IllegalArgumentException ex) {
            return Map.of(
                "success", false,
                "error", ex.getMessage()
            );
        }
    }

    @ResponseBody
    @DeleteMapping("/{id}")
    public Object deleteTerm(@PathVariable Long id) {
        if (!chatModerationTermService.deleteTerm(id)) {
            return Map.of(
                "success", false,
                "error", "Khong tim thay cum tu can chan trong database."
            );
        }
        return Map.of("success", true);
    }

    private Map<String, Object> toPayload(ChatModerationTermService.ModerationTermView term) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", term.id());
        payload.put("term", term.term());
        payload.put("source", term.source());
        payload.put("sourceLabel", "database".equalsIgnoreCase(term.source()) ? "Database" : "Mac dinh");
        payload.put("deletable", term.deletable());
        return payload;
    }

    public record CreateTermRequest(String term) {
    }
}
