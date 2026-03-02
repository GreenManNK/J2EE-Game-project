package com.game.hub.controller;

import com.game.hub.entity.Game;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.List;

@Controller("legacyGamesController")
@RequestMapping("/legacy/games")
public class GameController {

    @GetMapping
    public String games(Model model) {
        List<Game> games = new ArrayList<>();
        games.add(new Game("caro", "Caro", "Classic Tic-Tac-Toe", "bi-grid-3x3-gap-fill", true, true, true, "/games/caro"));
        games.add(new Game("chess", "Chess", "Classic Chess game", "bi-diagram-3-fill", true, true, true, "/games/chess"));
        games.add(new Game("xiangqi", "Xiangqi", "Chinese Chess game", "bi-diagram-3-fill", true, true, true, "/games/xiangqi"));
        games.add(new Game("minesweeper", "Minesweeper", "Classic Minesweeper game", "bi-asterisk", false, true, true, "/games/minesweeper"));
        games.add(new Game("quiz", "Quiz", "A time-limited quiz game", "bi-patch-question-fill", true, false, true, "/games/quiz"));
        games.add(new Game("cards", "Cards", "Tien Len + Blackjack", "bi-suit-spade-fill", true, true, true, "/games/cards"));
        games.add(new Game("typing", "Typing Battle", "PvP Typing Game", "bi-keyboard-fill", true, false, true, "/games/typing"));
        games.add(new Game("puzzle", "Puzzles", "Jigsaw, Sliding, Word and Sudoku", "bi-puzzle-fill", false, true, true, "/games/puzzle"));
        model.addAttribute("games", games);
        return "games/index";
    }
}
