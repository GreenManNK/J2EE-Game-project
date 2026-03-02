package com.game.hub.games.typing.controller;

import com.game.hub.games.typing.logic.TypingRoom;
import com.game.hub.games.typing.service.TypingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/games/typing")
public class TypingController {

    @Autowired
    private TypingService typingService;

    @GetMapping
    public String typingPage() {
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
        summary.put("gameState", room.getGameState());
        summary.put("textLength", room.getTextToType() == null ? 0 : room.getTextToType().length());
        return summary;
    }
}
