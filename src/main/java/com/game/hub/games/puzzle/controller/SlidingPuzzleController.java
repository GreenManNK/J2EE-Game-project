package com.game.hub.games.puzzle.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/games/puzzle/sliding")
public class SlidingPuzzleController {

    @GetMapping
    public String slidingPage() {
        return "games/puzzle/sliding";
    }
}
