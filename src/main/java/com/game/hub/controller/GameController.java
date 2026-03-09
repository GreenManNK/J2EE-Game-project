package com.game.hub.controller;

import com.game.hub.service.GameCatalogService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller("legacyGamesController")
@RequestMapping("/legacy/games")
public class GameController {
    private final GameCatalogService gameCatalogService;

    public GameController(GameCatalogService gameCatalogService) {
        this.gameCatalogService = gameCatalogService;
    }

    @GetMapping
    public String games(Model model) {
        model.addAttribute("games", gameCatalogService.findAll());
        return "games/index";
    }
}
