package com.game.hub.games.typing.controller;

import com.game.hub.games.typing.logic.TypingRoom;
import com.game.hub.games.typing.service.TypingService;
import com.game.hub.service.GameCatalogItem;
import com.game.hub.service.GameCatalogService;
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
@RequestMapping("/games/typing")
public class TypingController {
    private static final String GAME_CODE = "typing";

    @Autowired
    private TypingService typingService;

    @Autowired
    private GameCatalogService gameCatalogService;

    @GetMapping
    public String typingPage(@RequestParam(required = false) String room, Model model) {
        String normalizedRoomId = room == null ? "" : room.trim();
        if (!normalizedRoomId.isEmpty()) {
            return "redirect:/games/typing/room/" + UriUtils.encodePathSegment(normalizedRoomId, StandardCharsets.UTF_8);
        }
        return renderTypingPage(model, "", false);
    }

    @GetMapping("/room/{roomId}")
    public String typingRoomPage(@PathVariable String roomId, Model model) {
        return renderTypingPage(model, roomId, true);
    }

    @GetMapping("/practice")
    public String typingPracticePage(Model model) {
        return renderPracticePage(model, false, "easy");
    }

    @GetMapping("/bot")
    public String typingBotPage(@RequestParam(defaultValue = "easy") String difficulty, Model model) {
        return renderPracticePage(model, true, normalizeDifficulty(difficulty));
    }

    @GetMapping("/practice/texts")
    @ResponseBody
    public Map<String, Object> getPracticeTexts() {
        return Map.of(
            "texts", typingService.getPracticeTexts()
        );
    }

    private String renderTypingPage(Model model, String roomId, boolean roomPage) {
        populateCatalogModel(model);
        model.addAttribute("defaultRoomId", roomId == null ? "" : roomId.trim());
        model.addAttribute("roomPage", roomPage);
        return roomPage ? "games/typing-room" : "games/typing";
    }

    private String renderPracticePage(Model model, boolean botPage, String botDifficulty) {
        populateCatalogModel(model);
        model.addAttribute("practicePage", !botPage);
        model.addAttribute("botPage", botPage);
        model.addAttribute("botDifficulty", botDifficulty);
        return "games/typing-practice";
    }

    private void populateCatalogModel(Model model) {
        GameCatalogItem game = gameCatalogService.findByCode(GAME_CODE).orElse(null);
        model.addAttribute("game", game);
        model.addAttribute("allGames", gameCatalogService.findAll());
    }

    @GetMapping("/rooms/feed")
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
        summary.put("countdownEndsAtEpochMs", room.getCountdownEndsAtEpochMs());
        summary.put("raceStartedAtEpochMs", room.getRaceStartedAtEpochMs());
        summary.put("raceEndsAtEpochMs", room.getRaceEndsAtEpochMs());
        return summary;
    }

    private String normalizeDifficulty(String difficulty) {
        return "hard".equalsIgnoreCase(difficulty) ? "hard" : "easy";
    }
}
