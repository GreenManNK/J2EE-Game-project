package com.game.hub.games.puzzle.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/games/puzzle/jigsaw")
public class JigsawPuzzleController {

    @GetMapping
    public String jigsawPage() {
        return "games/puzzle/jigsaw";
    }
}
