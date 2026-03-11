package com.game.hub.games.typing.controller;

import com.game.hub.games.typing.logic.TypingRoom;
import com.game.hub.games.typing.service.TypingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/games/typing")
public class TypingController {

    @Autowired
    private TypingService typingService;

    @GetMapping
    public String typingPage(@RequestParam(required = false) String room) {
        String normalizedRoomId = room == null ? "" : room.trim();
        if (!normalizedRoomId.isEmpty()) {
            return "redirect:/games/typing/room/" + UriUtils.encodePathSegment(normalizedRoomId, StandardCharsets.UTF_8);
        }
        return "redirect:/online-hub?game=typing";
    }

    @GetMapping("/room/{roomId}")
    public String typingRoomPage(@PathVariable String roomId) {
        return renderTypingPage();
    }

    private String renderTypingPage() {
        return "games/typing";
    }

    @GetMapping("/rooms")
    @ResponseBody
    public List<Map<String, Object>> getAvailableRooms() {
        return typingService.getAvailableRooms().stream()
            .filter(room -> room.getPlayerCount() > 0)
            .map(this::toRoomSummary)
            .toList();
    }

    private Map<String, Object> toRoomSummary(TypingRoom room) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", room.getId());
        summary.put("playerCount", room.getPlayerCount());
        summary.put("playerLimit", room.getPlayerLimit());
        summary.put("gameState", room.getGameState());
        summary.put("textLength", room.getTextToType() == null ? 0 : room.getTextToType().length());
        return summary;
    }
}
