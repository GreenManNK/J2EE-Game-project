package com.game.hub.games.puzzle.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/games/puzzle/sudoku")
public class SudokuController {

    @GetMapping
    public String sudokuPage() {
        return "games/puzzle/sudoku";
    }
}
