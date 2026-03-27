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
        return renderPage(model, "", false, true, false, "easy");
    }

    @GetMapping("/bot")
    public String botPage(@RequestParam(defaultValue = "easy") String difficulty, Model model) {
        return renderPage(model, "", false, false, true, normalizeDifficulty(difficulty));
    }

    @GetMapping("/room/{roomId}")
    public String roomPage(@PathVariable String roomId, Model model) {
        return renderPage(model, roomId, true, false, false, "easy");
    }

    private String renderPage(Model model,
                              String roomId,
                              boolean roomPage,
                              boolean localPage,
                              boolean botPage,
                              String botDifficulty) {
        GameCatalogItem game = gameCatalogService.findByCode(GAME_CODE).orElse(null);
        model.addAttribute("game", game);
        model.addAttribute("allGames", gameCatalogService.findAll());
        model.addAttribute("defaultRoomId", roomId == null ? "" : roomId.trim());
        model.addAttribute("roomPage", roomPage);
        model.addAttribute("localPage", localPage);
        model.addAttribute("botPage", botPage);
        model.addAttribute("botDifficulty", normalizeDifficulty(botDifficulty));
        return "games/monopoly";
    }

    private String normalizeDifficulty(String difficulty) {
        return "hard".equalsIgnoreCase(difficulty) ? "hard" : "easy";
    }
}
