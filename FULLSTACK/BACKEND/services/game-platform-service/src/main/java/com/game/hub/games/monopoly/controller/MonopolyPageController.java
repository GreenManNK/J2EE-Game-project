package com.game.hub.games.monopoly.controller;

import com.game.hub.service.GameCatalogItem;
import com.game.hub.service.GameCatalogService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/games/monopoly")
public class MonopolyPageController {
    private static final String GAME_CODE = "monopoly";

    private final GameCatalogService gameCatalogService;

    public MonopolyPageController(GameCatalogService gameCatalogService) {
        this.gameCatalogService = gameCatalogService;
    }

    @GetMapping("/rooms")
    public String roomsPage() {
        return "redirect:/games/monopoly";
    }

    @GetMapping("/local")
    public String localPage(Model model) {
        return renderPage(model, "", false, true, false, false, "easy", "", 4, 1500);
    }

    @GetMapping("/bot")
    public String botPage(@RequestParam(defaultValue = "easy") String difficulty, Model model) {
        return renderPage(model, "", false, false, true, false, normalizeDifficulty(difficulty), "", 4, 1500);
    }

    @GetMapping("/bot/arena")
    public String botArenaPage(@RequestParam(defaultValue = "easy") String difficulty,
                               @RequestParam(defaultValue = "Ty phu 1") String playerName,
                               @RequestParam(defaultValue = "4") int playerCount,
                               @RequestParam(defaultValue = "1500") int startingCash,
                               Model model) {
        return renderPage(
            model,
            "",
            false,
            false,
            true,
            true,
            normalizeDifficulty(difficulty),
            playerName,
            Math.max(2, Math.min(4, playerCount)),
            Math.max(800, startingCash)
        );
    }

    @GetMapping("/room/{roomId}")
    public String roomPage(@PathVariable String roomId, Model model) {
        return renderPage(model, roomId, true, false, false, false, "easy", "", 4, 1500);
    }

    private String renderPage(Model model,
                              String roomId,
                              boolean roomPage,
                              boolean localPage,
                              boolean botPage,
                              boolean botArenaPage,
                              String botDifficulty,
                              String botPlayerName,
                              int botPlayerCount,
                              int botStartingCash) {
        GameCatalogItem game = gameCatalogService.findByCode(GAME_CODE).orElse(null);
        model.addAttribute("game", game);
        model.addAttribute("allGames", gameCatalogService.findAll());
        model.addAttribute("defaultRoomId", roomId == null ? "" : roomId.trim());
        model.addAttribute("roomPage", roomPage);
        model.addAttribute("localPage", localPage);
        model.addAttribute("botPage", botPage);
        model.addAttribute("botArenaPage", botArenaPage);
        model.addAttribute("botDifficulty", normalizeDifficulty(botDifficulty));
        model.addAttribute("botPlayerName", botPlayerName == null ? "" : botPlayerName.trim());
        model.addAttribute("botPlayerCount", Math.max(2, Math.min(4, botPlayerCount)));
        model.addAttribute("botStartingCash", Math.max(800, botStartingCash));
        if (roomPage) {
            return "games/monopoly-room";
        }
        if (botArenaPage) {
            return "games/monopoly-bot-arena";
        }
        if (botPage) {
            return "games/monopoly-bot";
        }
        if (localPage) {
            return "games/monopoly-local";
        }
        return "games/monopoly";
    }

    private String normalizeDifficulty(String difficulty) {
        return "hard".equalsIgnoreCase(difficulty) ? "hard" : "easy";
    }
}
