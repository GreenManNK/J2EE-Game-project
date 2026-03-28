package com.game.hub.games.cards.blackjack.controller;

import com.game.hub.games.cards.blackjack.logic.BlackjackRoom;
import com.game.hub.games.cards.blackjack.service.BlackjackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
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
@RequestMapping("/games/cards/blackjack")
public class BlackjackController {

    @Autowired
    private BlackjackService blackjackService;

    @GetMapping
    public String blackjackPage(@RequestParam(required = false) String room,
                                @RequestParam(required = false) String mode,
                                Model model) {
        String normalizedRoomId = room == null ? "" : room.trim();
        if (!normalizedRoomId.isEmpty()) {
            StringBuilder redirect = new StringBuilder("redirect:/games/cards/blackjack/room/")
                .append(UriUtils.encodePathSegment(normalizedRoomId, StandardCharsets.UTF_8));
            if ("spectate".equalsIgnoreCase(mode)) {
                redirect.append("/spectate");
            }
            return redirect.toString();
        }
        return renderBlackjackPage(model, "", false, false);
    }

    @GetMapping("/room/{roomId}")
    public String blackjackRoomPage(@PathVariable String roomId, Model model) {
        return renderBlackjackPage(model, roomId, false, true);
    }

    @GetMapping("/room/{roomId}/spectate")
    public String blackjackSpectatePage(@PathVariable String roomId, Model model) {
        return renderBlackjackPage(model, roomId, true, true);
    }

    @GetMapping("/bot")
    public String blackjackBotPage() {
        return "games/cards/blackjack-bot";
    }

    @GetMapping("/local")
    public String blackjackLocalPage(Model model) {
        model.addAttribute("localPage", true);
        return "games/cards/blackjack-local";
    }

    private String renderBlackjackPage(Model model,
                                       String roomId,
                                       boolean spectateMode,
                                       boolean roomPage) {
        model.addAttribute("defaultRoomId", roomId == null ? "" : roomId.trim());
        model.addAttribute("spectateMode", spectateMode);
        model.addAttribute("roomPage", roomPage);
        return roomPage ? "games/cards/blackjack-room" : "games/cards/blackjack";
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
        summary.put("playerLimit", room.getPlayerLimit());
        summary.put("spectatorCount", room.getSpectators().size());
        summary.put("spectatorLimit", room.getSpectatorLimit());
        summary.put("gameState", room.getGameState());
        summary.put("currentTurnPlayerId", room.getCurrentTurnPlayerId());
        return summary;
    }
}
