package com.game.hub.games.puzzle.controller;

import com.game.hub.service.AchievementService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;

@Controller
@RequestMapping("/games/puzzle/word")
public class WordPuzzleController {
    private final AchievementService achievementService;

    public WordPuzzleController(AchievementService achievementService) {
        this.achievementService = achievementService;
    }

    @GetMapping
    public String wordPage() {
        return "games/puzzle/word";
    }

    @PostMapping("/win")
    @ResponseBody
    public void onWin(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            return;
        }
        achievementService.checkAndAward(principal.getName(), "Puzzle Word", true);
    }
}
