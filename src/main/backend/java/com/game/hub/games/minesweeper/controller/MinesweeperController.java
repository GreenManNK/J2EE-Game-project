package com.game.hub.games.minesweeper.controller;

import com.game.hub.service.AchievementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;

@Controller
@RequestMapping("/minesweeper")
public class MinesweeperController {

    @Autowired
    private AchievementService achievementService;

    @GetMapping
    public String index(@RequestParam(defaultValue = "beginner") String level, Model model) {
        if (model != null) {
            model.addAttribute("initialLevel", normalizeLevel(level));
        }
        return "minesweeper/index";
    }

    @PostMapping("/win")
    @ResponseBody
    public void win(Principal principal) {
        if (principal != null) {
            achievementService.checkAndAward(principal.getName(), "Minesweeper", true);
        }
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
