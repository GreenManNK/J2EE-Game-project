package com.game.hub.games.puzzle.controller;

import com.game.hub.service.GameCatalogItem;
import com.game.hub.service.GameCatalogService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/games/puzzle")
public class PuzzleController {
    private static final String GAME_CODE = "puzzle";
    private final GameCatalogService gameCatalogService;

    public PuzzleController(GameCatalogService gameCatalogService) {
        this.gameCatalogService = gameCatalogService;
    }

    @GetMapping
    public String puzzlePage(Model model) {
        GameCatalogItem game = gameCatalogService.findByCode(GAME_CODE).orElse(null);
        model.addAttribute("game", game);
        model.addAttribute("allGames", gameCatalogService.findAll());
        return "games/puzzle/index";
    }
}
