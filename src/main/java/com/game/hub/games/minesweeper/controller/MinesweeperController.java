package com.game.hub.games.minesweeper.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/minesweeper")
public class MinesweeperController {

    @GetMapping
    public String index(@RequestParam(defaultValue = "beginner") String level, Model model) {
        if (model != null) {
            model.addAttribute("initialLevel", normalizeLevel(level));
        }
        return "minesweeper/index";
    }

    private String normalizeLevel(String value) {
        if ("intermediate".equalsIgnoreCase(value)) {
            return "intermediate";
        }
        if ("expert".equalsIgnoreCase(value)) {
            return "expert";
        }
        return "beginner";
    }
}
