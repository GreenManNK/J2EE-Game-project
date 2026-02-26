package com.game.hub.controller;

import com.game.hub.service.GameCatalogItem;
import com.game.hub.service.GameCatalogService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Controller
@RequestMapping("/games")
public class GameCatalogController {
    private final GameCatalogService gameCatalogService;

    public GameCatalogController(GameCatalogService gameCatalogService) {
        this.gameCatalogService = gameCatalogService;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("games", gameCatalogService.findAll());
        return "games/index";
    }

    @ResponseBody
    @GetMapping("/api")
    public Map<String, Object> apiIndex() {
        return Map.of("games", gameCatalogService.findAll());
    }

    @GetMapping("/{code}")
    public String detail(@PathVariable String code, Model model) {
        GameCatalogItem game = gameCatalogService.findByCode(code)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));
        model.addAttribute("game", game);
        model.addAttribute("allGames", gameCatalogService.findAll());
        return switch (game.code()) {
            case "caro" -> "games/caro";
            case "chess" -> "games/chess";
            case "xiangqi" -> "games/xiangqi";
            case "cards" -> "games/cards";
            default -> "games/detail";
        };
    }
}
