package com.game.hub.games.caro.controller;

import com.game.hub.games.caro.logic.BotEasy;
import com.game.hub.games.caro.logic.BotHard;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/bot")
public class BotController {
    private static final int SIZE = 15;
    private static final String EASY_STATE_KEY = "BOT_EASY_STATE";
    private static final String HARD_STATE_KEY = "BOT_HARD_STATE";
    private static final Object EASY_ENGINE_LOCK = new Object();
    private static final Object HARD_ENGINE_LOCK = new Object();

    @GetMapping("/easy")
    public String easy(Model model) {
        model.addAttribute("mode", "easy");
        model.addAttribute("title", "Bot De");
        return "bot/play";
    }

    @GetMapping("/hard")
    public String hard(Model model) {
        model.addAttribute("mode", "hard");
        model.addAttribute("title", "Bot Kho");
        return "bot/play";
    }

    @ResponseBody
    @PostMapping("/easy-move")
    public Map<String, Object> easyMove(@RequestBody MoveRequest move, HttpSession session) {
        if (!inside(move)) {
            return Map.of("success", false, "error", "Invalid position");
        }

        BotSessionState state = getOrCreateState(session, EASY_STATE_KEY);
        if (state.isOccupied(move.x(), move.y())) {
            return Map.of("success", false, "error", "Cell already occupied");
        }

        synchronized (EASY_ENGINE_LOCK) {
            BotEasy.resetBoard();
            replayEasy(state);

            BotEasy.placePlayerMove(move.x(), move.y());
            BotEasy.WinResult playerWinResult = BotEasy.checkWinResult('X');

            state.addPlayerMove(move.x(), move.y());
            state.mark(move.x(), move.y(), 'X');

            if (playerWinResult.hasWin()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("x", null);
                response.put("y", null);
                response.put("playerWin", true);
                response.put("botWin", false);
                response.put("draw", false);
                response.put("winLine", playerWinResult.winLine());
                return response;
            }

            if (state.isBoardFull()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("x", null);
                response.put("y", null);
                response.put("playerWin", false);
                response.put("botWin", false);
                response.put("draw", true);
                response.put("winLine", null);
                return response;
            }

            BotEasy.Move botMove = resolveEasyBotMove(BotEasy.getNextMove(move.x(), move.y()), state);
            if (botMove == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("x", null);
                response.put("y", null);
                response.put("playerWin", false);
                response.put("botWin", false);
                response.put("draw", true);
                response.put("winLine", null);
                return response;
            }

            state.addBotMove(botMove.x(), botMove.y());
            state.mark(botMove.x(), botMove.y(), 'O');
            BotEasy.WinResult botWinResult = BotEasy.checkWinResult('O');
            boolean draw = !botWinResult.hasWin() && state.isBoardFull();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("x", botMove.x());
            response.put("y", botMove.y());
            response.put("playerWin", false);
            response.put("botWin", botWinResult.hasWin());
            response.put("draw", draw);
            response.put("winLine", botWinResult.hasWin() ? botWinResult.winLine() : null);
            return response;
        }
    }

    @ResponseBody
    @PostMapping("/hard-move")
    public Map<String, Object> hardMove(@RequestBody MoveRequest move, HttpSession session) {
        if (!inside(move)) {
            return Map.of("success", false, "error", "Invalid position");
        }

        BotSessionState state = getOrCreateState(session, HARD_STATE_KEY);
        if (state.isOccupied(move.x(), move.y())) {
            return Map.of("success", false, "error", "Cell already occupied");
        }

        synchronized (HARD_ENGINE_LOCK) {
            BotHard.resetBoard();
            replayHard(state);

            BotHard.placePlayerMove(move.x(), move.y());
            BotHard.WinResult playerWinResult = BotHard.checkWin('X');

            state.addPlayerMove(move.x(), move.y());
            state.mark(move.x(), move.y(), 'X');

            if (playerWinResult.hasWin()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("x", null);
                response.put("y", null);
                response.put("playerWin", true);
                response.put("botWin", false);
                response.put("draw", false);
                response.put("winLine", playerWinResult.winLine());
                return response;
            }

            if (state.isBoardFull()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("x", null);
                response.put("y", null);
                response.put("playerWin", false);
                response.put("botWin", false);
                response.put("draw", true);
                response.put("winLine", null);
                return response;
            }

            BotHard.Move botMove = resolveHardBotMove(BotHard.getNextMove(move.x(), move.y()), state);
            if (botMove == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("x", null);
                response.put("y", null);
                response.put("playerWin", false);
                response.put("botWin", false);
                response.put("draw", true);
                response.put("winLine", null);
                return response;
            }

            state.addBotMove(botMove.x(), botMove.y());
            state.mark(botMove.x(), botMove.y(), 'O');
            BotHard.WinResult botWinResult = BotHard.checkWin('O');
            boolean draw = !botWinResult.hasWin() && state.isBoardFull();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("x", botMove.x());
            response.put("y", botMove.y());
            response.put("playerWin", false);
            response.put("botWin", botWinResult.hasWin());
            response.put("draw", draw);
            if (botWinResult.hasWin()) {
                response.put("winLine", botWinResult.winLine());
            } else {
                response.put("winLine", null);
            }
            return response;
        }
    }

    @ResponseBody
    @PostMapping("/reset")
    public Map<String, Object> reset(HttpSession session) {
        if (session != null) {
            session.removeAttribute(EASY_STATE_KEY);
            session.removeAttribute(HARD_STATE_KEY);
        }
        synchronized (EASY_ENGINE_LOCK) {
            BotEasy.resetBoard();
        }
        synchronized (HARD_ENGINE_LOCK) {
            BotHard.resetBoard();
        }
        return Map.of("success", true);
    }

    public record MoveRequest(int x, int y) {
    }

    private boolean inside(MoveRequest move) {
        return move != null && move.x() >= 0 && move.x() < SIZE && move.y() >= 0 && move.y() < SIZE;
    }

    private boolean inside(BotEasy.Move move) {
        return move != null && move.x() >= 0 && move.x() < SIZE && move.y() >= 0 && move.y() < SIZE;
    }

    private boolean inside(BotHard.Move move) {
        return move != null && move.x() >= 0 && move.x() < SIZE && move.y() >= 0 && move.y() < SIZE;
    }

    private BotSessionState getOrCreateState(HttpSession session, String key) {
        if (session == null) {
            return new BotSessionState();
        }
        Object existing = session.getAttribute(key);
        if (existing instanceof BotSessionState state) {
            return state;
        }
        BotSessionState state = new BotSessionState();
        session.setAttribute(key, state);
        return state;
    }

    private void replayEasy(BotSessionState state) {
        List<int[]> playerMoves = state.playerMoves == null ? List.of() : state.playerMoves;
        List<int[]> botMoves = state.botMoves == null ? List.of() : state.botMoves;
        for (int i = 0; i < playerMoves.size(); i++) {
            int[] playerMove = playerMoves.get(i);
            BotEasy.placePlayerMove(playerMove[0], playerMove[1]);
            if (i < botMoves.size()) {
                int[] botMove = botMoves.get(i);
                BotEasy.placeBotMove(botMove[0], botMove[1]);
            }
        }
    }

    private void replayHard(BotSessionState state) {
        List<int[]> playerMoves = state.playerMoves == null ? List.of() : state.playerMoves;
        List<int[]> botMoves = state.botMoves == null ? List.of() : state.botMoves;
        for (int i = 0; i < playerMoves.size(); i++) {
            int[] playerMove = playerMoves.get(i);
            BotHard.placePlayerMove(playerMove[0], playerMove[1]);
            if (i < botMoves.size()) {
                int[] botMove = botMoves.get(i);
                BotHard.placeBotMove(botMove[0], botMove[1]);
            }
        }
    }

    private BotEasy.Move resolveEasyBotMove(BotEasy.Move move, BotSessionState state) {
        if (inside(move) && !state.isOccupied(move.x(), move.y())) {
            return move;
        }
        int[] fallback = state.findFirstEmpty();
        if (fallback == null) {
            return null;
        }
        BotEasy.placeBotMove(fallback[0], fallback[1]);
        return new BotEasy.Move(fallback[0], fallback[1]);
    }

    private BotHard.Move resolveHardBotMove(BotHard.Move move, BotSessionState state) {
        if (inside(move) && !state.isOccupied(move.x(), move.y())) {
            return move;
        }
        int[] fallback = state.findFirstEmpty();
        if (fallback == null) {
            return null;
        }
        BotHard.placeBotMove(fallback[0], fallback[1]);
        return new BotHard.Move(fallback[0], fallback[1]);
    }

    private static final class BotSessionState implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final char[][] board = new char[SIZE][SIZE];
        private final List<int[]> playerMoves = new ArrayList<>();
        private List<int[]> botMoves = new ArrayList<>();

        boolean isOccupied(int x, int y) {
            return board[x][y] != '\0';
        }

        void mark(int x, int y, char piece) {
            board[x][y] = piece;
        }

        void addPlayerMove(int x, int y) {
            playerMoves.add(new int[]{x, y});
        }

        void addBotMove(int x, int y) {
            if (botMoves == null) {
                botMoves = new ArrayList<>();
            }
            botMoves.add(new int[]{x, y});
        }

        boolean isBoardFull() {
            return findFirstEmpty() == null;
        }

        int[] findFirstEmpty() {
            for (int x = 0; x < SIZE; x++) {
                for (int y = 0; y < SIZE; y++) {
                    if (board[x][y] == '\0') {
                        return new int[]{x, y};
                    }
                }
            }
            return null;
        }
    }
}
