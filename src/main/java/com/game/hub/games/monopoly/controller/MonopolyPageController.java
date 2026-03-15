package com.game.hub.games.monopoly.controller;

import com.game.hub.service.GameCatalogItem;
import com.game.hub.service.GameCatalogService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

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
        return renderPage(model, "", false, true);
    }

    @GetMapping("/room/{roomId}")
    public String roomPage(@PathVariable String roomId, Model model) {
        return renderPage(model, roomId, true, false);
    }

    private String renderPage(Model model,
                              String roomId,
                              boolean roomPage,
                              boolean localPage) {
        GameCatalogItem game = gameCatalogService.findByCode(GAME_CODE).orElse(null);
        model.addAttribute("game", game);
        model.addAttribute("allGames", gameCatalogService.findAll());
        model.addAttribute("defaultRoomId", roomId == null ? "" : roomId.trim());
        model.addAttribute("roomPage", roomPage);
        model.addAttribute("localPage", localPage);
        return "games/monopoly";
    }
}
