package com.game.hub.games.puzzle.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/games/puzzle/word")
public class WordPuzzleController {

    @GetMapping
    public String wordPage() {
        return "games/puzzle/word";
    }
}
