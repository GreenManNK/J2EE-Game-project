package com.caro.game.controller;

import com.caro.game.logic.BotEasy;
import com.caro.game.logic.BotHard;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/bot")
public class BotController {

    @GetMapping("/easy")
    public Map<String, Object> easy() {
        return Map.of("mode", "easy");
    }

    @GetMapping("/hard")
    public Map<String, Object> hard() {
        return Map.of("mode", "hard");
    }

    @PostMapping("/easy-move")
    public Map<String, Object> easyMove(@RequestBody MoveRequest move) {
        BotEasy.placePlayerMove(move.x(), move.y());

        boolean playerWin = BotEasy.checkWin('X');
        if (playerWin) {
            Map<String, Object> response = new HashMap<>();
            response.put("x", null);
            response.put("y", null);
            response.put("playerWin", true);
            response.put("botWin", false);
            return response;
        }

        BotEasy.Move botMove = BotEasy.getNextMove(move.x(), move.y());
        boolean botWin = BotEasy.checkWin('O');

        return Map.of(
            "x", botMove.x(),
            "y", botMove.y(),
            "playerWin", false,
            "botWin", botWin
        );
    }

    @PostMapping("/hard-move")
    public Map<String, Object> hardMove(@RequestBody MoveRequest move) {
        BotHard.Move botMove = BotHard.getNextMove(move.x(), move.y());
        BotHard.WinResult playerWinResult = BotHard.checkWin('X');
        BotHard.WinResult botWinResult = BotHard.checkWin('O');

        Map<String, Object> response = new HashMap<>();
        response.put("x", botMove.x());
        response.put("y", botMove.y());
        response.put("playerWin", playerWinResult.hasWin());
        response.put("botWin", botWinResult.hasWin());
        if (playerWinResult.hasWin()) {
            response.put("winLine", playerWinResult.winLine());
        } else if (botWinResult.hasWin()) {
            response.put("winLine", botWinResult.winLine());
        } else {
            response.put("winLine", null);
        }
        return response;
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        BotEasy.resetBoard();
        BotHard.resetBoard();
        return Map.of("success", true);
    }

    public record MoveRequest(int x, int y) {
    }
}
