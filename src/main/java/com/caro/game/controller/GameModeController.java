package com.caro.game.controller;

import com.caro.game.service.GameCatalogItem;
import com.caro.game.service.GameCatalogService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/game-mode")
public class GameModeController {
    private final GameCatalogService gameCatalogService;

    public GameModeController(GameCatalogService gameCatalogService) {
        this.gameCatalogService = gameCatalogService;
    }

    @GetMapping("/bot")
    public String botPicker(@RequestParam String game, Model model) {
        GameCatalogItem item = gameCatalogService.findByCode(game)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));
        model.addAttribute("game", item);
        model.addAttribute("hasRealBotNow", hasRealBotNow(item.code()));
        model.addAttribute("easyUrl", botPlayUrl(item.code(), "easy"));
        model.addAttribute("hardUrl", botPlayUrl(item.code(), "hard"));
        return "game-mode/bot-picker";
    }

    @GetMapping("/bot-play")
    public String botPlayPlaceholder(@RequestParam String game,
                                     @RequestParam(defaultValue = "easy") String difficulty,
                                     Model model) {
        GameCatalogItem item = gameCatalogService.findByCode(game)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));
        String normalizedDifficulty = normalizeDifficulty(difficulty);
        if ("caro".equalsIgnoreCase(item.code())) {
            return "redirect:/bot/" + normalizedDifficulty;
        }
        if ("chess".equalsIgnoreCase(item.code())) {
            return "redirect:/chess/bot?difficulty=" + normalizedDifficulty;
        }
        if ("xiangqi".equalsIgnoreCase(item.code())) {
            return "redirect:/xiangqi/bot?difficulty=" + normalizedDifficulty;
        }
        if ("cards".equalsIgnoreCase(item.code())) {
            return "redirect:/cards/tien-len/bot?difficulty=" + normalizedDifficulty;
        }
        model.addAttribute("game", item);
        model.addAttribute("difficulty", normalizedDifficulty);
        model.addAttribute("difficultyLabel", "hard".equals(normalizedDifficulty) ? "Kho" : "De");
        model.addAttribute("backToPickerUrl", "/game-mode/bot?game=" + item.code());
        return "game-mode/bot-placeholder";
    }

    private String botPlayUrl(String gameCode, String difficulty) {
        String d = normalizeDifficulty(difficulty);
        if ("caro".equalsIgnoreCase(gameCode)) {
            return "/bot/" + d;
        }
        if ("chess".equalsIgnoreCase(gameCode)) {
            return "/chess/bot?difficulty=" + d;
        }
        if ("xiangqi".equalsIgnoreCase(gameCode)) {
            return "/xiangqi/bot?difficulty=" + d;
        }
        if ("cards".equalsIgnoreCase(gameCode)) {
            return "/cards/tien-len/bot?difficulty=" + d;
        }
        return "/game-mode/bot-play?game=" + gameCode + "&difficulty=" + d;
    }

    private boolean hasRealBotNow(String gameCode) {
        return "caro".equalsIgnoreCase(gameCode)
            || "chess".equalsIgnoreCase(gameCode)
            || "xiangqi".equalsIgnoreCase(gameCode)
            || "cards".equalsIgnoreCase(gameCode);
    }

    private String normalizeDifficulty(String difficulty) {
        return "hard".equalsIgnoreCase(difficulty) ? "hard" : "easy";
    }
}
