package com.game.hub.games.puzzle.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/games/puzzle")
public class PuzzleController {

    @GetMapping
    public String puzzlePage() {
        return "games/puzzle/index";
    }
}
