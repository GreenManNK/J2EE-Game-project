package com.game.hub.games.cards.blackjack.controller;

import com.game.hub.games.cards.blackjack.logic.BlackjackRoom;
import com.game.hub.games.cards.blackjack.service.BlackjackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/games/cards/blackjack")
public class BlackjackController {

    @Autowired
    private BlackjackService blackjackService;

    @GetMapping
    public String blackjackPage() {
        return "games/cards/blackjack";
    }

    @GetMapping("/room/{roomId}")
    public String blackjackRoomPage(@PathVariable String roomId) {
        return blackjackPage();
    }

    @GetMapping("/room/{roomId}/spectate")
    public String blackjackSpectatePage(@PathVariable String roomId) {
        return blackjackPage();
    }

    @GetMapping("/rooms")
    @ResponseBody
    public List<Map<String, Object>> getAvailableRooms() {
        return blackjackService.getAvailableRooms().stream()
            .filter(room -> !room.getPlayers().isEmpty() || !room.getSpectators().isEmpty())
            .map(this::toRoomSummary)
            .toList();
    }

    private Map<String, Object> toRoomSummary(BlackjackRoom room) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", room.getId());
        summary.put("playerCount", room.getPlayers().size());
        summary.put("spectatorCount", room.getSpectators().size());
        summary.put("gameState", room.getGameState());
        return summary;
    }
}
