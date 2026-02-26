package com.game.hub.games.caro.logic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class BotHard {
    private static final int SIZE = 15;
    private static final char[][] BOARD = new char[SIZE][SIZE];

    private static Move lastPlayerMove = new Move(7, 7);
    private static Move lastBotMove = new Move(7, 8);

    private BotHard() {
    }

    public static synchronized void placePlayerMove(int x, int y) {
        if (inside(x, y)) {
            BOARD[x][y] = 'X';
            lastPlayerMove = new Move(x, y);
        }
    }

    public static synchronized void placeBotMove(int x, int y) {
        if (inside(x, y)) {
            BOARD[x][y] = 'O';
            lastBotMove = new Move(x, y);
        }
    }

    public static synchronized Move getNextMove(int lastPlayerX, int lastPlayerY) {
        if (inside(lastPlayerX, lastPlayerY)) {
            BOARD[lastPlayerX][lastPlayerY] = 'X';
            lastPlayerMove = new Move(lastPlayerX, lastPlayerY);
        }

        int bestScore = Integer.MIN_VALUE;
        Move bestMove = new Move(-1, -1);

        for (Move move : getFocusedValidMoves(lastPlayerMove, lastBotMove)) {
            if (!inside(move.x(), move.y())) {
                continue;
            }
            BOARD[move.x()][move.y()] = 'O';
            int score = minimax(3, false, Integer.MIN_VALUE, Integer.MAX_VALUE);
            BOARD[move.x()][move.y()] = '\0';

            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
        }

        lastBotMove = bestMove;
        if (inside(bestMove.x(), bestMove.y()) && BOARD[bestMove.x()][bestMove.y()] == '\0') {
            BOARD[bestMove.x()][bestMove.y()] = 'O';
        }
        return bestMove;
    }

    public static synchronized WinResult checkWin(char player) {
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                if (BOARD[i][j] == player) {
                    int[][] dirs = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
                    for (int[] dir : dirs) {
                        List<Move> line = new ArrayList<>();
                        int x = i;
                        int y = j;
                        while (inside(x, y) && BOARD[x][y] == player) {
                            line.add(new Move(x, y));
                            x += dir[0];
                            y += dir[1];
                        }
                        if (line.size() >= 5) {
                            return new WinResult(true, line);
                        }
                    }
                }
            }
        }
        return new WinResult(false, List.of());
    }

    public static synchronized void resetBoard() {
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                BOARD[i][j] = '\0';
            }
        }
        lastPlayerMove = new Move(7, 7);
        lastBotMove = new Move(7, 8);
    }

    private static List<Move> getFocusedValidMoves(Move lastPlayer, Move lastBot) {
        Set<Move> moves = new HashSet<>();
        List<Move> centers = List.of(lastPlayer, lastBot);

        for (Move center : centers) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    int nx = center.x() + dx;
                    int ny = center.y() + dy;
                    if (inside(nx, ny) && BOARD[nx][ny] == '\0') {
                        moves.add(new Move(nx, ny));
                    }
                }
            }
        }

        if (moves.isEmpty()) {
            Move fallback = findAnyEmpty();
            if (fallback != null) {
                moves.add(fallback);
            }
        }

        return new ArrayList<>(moves);
    }

    private static boolean inside(int x, int y) {
        return x >= 0 && x < SIZE && y >= 0 && y < SIZE;
    }

    private static Move findAnyEmpty() {
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                if (BOARD[i][j] == '\0') {
                    return new Move(i, j);
                }
            }
        }
        return null;
    }

    private static int minimax(int depth, boolean maximizing, int alpha, int beta) {
        if (depth == 0 || checkWin('X').hasWin() || checkWin('O').hasWin()) {
            return evaluateBoard();
        }

        List<Move> moves = getFocusedValidMoves(lastPlayerMove, lastBotMove);

        if (maximizing) {
            int maxEval = Integer.MIN_VALUE;
            for (Move move : moves) {
                if (!inside(move.x(), move.y())) continue;

                BOARD[move.x()][move.y()] = 'O';
                int eval = minimax(depth - 1, false, alpha, beta);
                BOARD[move.x()][move.y()] = '\0';

                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);
                if (beta <= alpha) break;
            }
            return maxEval;
        }

        int minEval = Integer.MAX_VALUE;
        for (Move move : moves) {
            if (!inside(move.x(), move.y())) continue;

            BOARD[move.x()][move.y()] = 'X';
            int eval = minimax(depth - 1, true, alpha, beta);
            BOARD[move.x()][move.y()] = '\0';

            minEval = Math.min(minEval, eval);
            beta = Math.min(beta, eval);
            if (beta <= alpha) break;
        }
        return minEval;
    }

    private static int evaluateBoard() {
        return score('O') - score('X');
    }

    private static int score(char player) {
        int total = 0;
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                if (BOARD[i][j] == player) {
                    total += lineScore(i, j, player, 1, 0);
                    total += lineScore(i, j, player, 0, 1);
                    total += lineScore(i, j, player, 1, 1);
                    total += lineScore(i, j, player, 1, -1);
                }
            }
        }
        return total;
    }

    private static int lineScore(int x, int y, char player, int dx, int dy) {
        int count = 0;
        for (int i = 0; i < 5; i++) {
            int nx = x + i * dx;
            int ny = y + i * dy;
            if (inside(nx, ny) && BOARD[nx][ny] == player) {
                count++;
            } else {
                break;
            }
        }

        return switch (count) {
            case 5 -> 100000;
            case 4 -> 10000;
            case 3 -> 1000;
            case 2 -> 100;
            default -> 0;
        };
    }

    public record Move(int x, int y) {
    }

    public record WinResult(boolean hasWin, List<Move> winLine) {
    }
}
