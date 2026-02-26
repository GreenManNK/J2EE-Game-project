package com.game.hub.games.caro.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class BotEasy {
    private static final int SIZE = 15;
    private static final char[][] BOARD = new char[SIZE][SIZE];
    private static final Random RANDOM = new Random();

    private BotEasy() {
    }

    public static synchronized void placePlayerMove(int x, int y) {
        if (inside(x, y)) {
            BOARD[x][y] = 'X';
        }
    }

    public static synchronized void placeBotMove(int x, int y) {
        if (inside(x, y)) {
            BOARD[x][y] = 'O';
        }
    }

    public static synchronized Move getNextMove(int lastPlayerX, int lastPlayerY) {
        if (inside(lastPlayerX, lastPlayerY)) {
            BOARD[lastPlayerX][lastPlayerY] = 'X';
        }

        if (countPieces('O') == 0) {
            Move first = findFirstPlayerMove();
            Move around = placeAround(first.x(), first.y());
            if (around != null) {
                return around;
            }
            return findAnyEmpty();
        }

        Move block = blockPlayer();
        if (block != null) {
            return block;
        }

        Move expand = expandBot();
        if (expand != null) {
            return expand;
        }

        Move pattern = createPattern();
        if (pattern != null) {
            return pattern;
        }

        return findAnyEmpty();
    }

    public static synchronized boolean checkWin(char piece) {
        return checkWinResult(piece).hasWin();
    }

    public static synchronized WinResult checkWinResult(char piece) {
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                if (BOARD[i][j] == piece) {
                    List<Move> line = collectLine(i, j, piece, 1, 0);
                    if (line.size() >= 5) {
                        return new WinResult(true, line);
                    }
                    line = collectLine(i, j, piece, 0, 1);
                    if (line.size() >= 5) {
                        return new WinResult(true, line);
                    }
                    line = collectLine(i, j, piece, 1, 1);
                    if (line.size() >= 5) {
                        return new WinResult(true, line);
                    }
                    line = collectLine(i, j, piece, 1, -1);
                    if (line.size() >= 5) {
                        return new WinResult(true, line);
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
    }

    private static int countConsecutive(int x, int y, char piece, int dx, int dy) {
        int count = 0;
        while (inside(x, y) && BOARD[x][y] == piece) {
            count++;
            x += dx;
            y += dy;
        }
        return count;
    }

    private static List<Move> collectLine(int x, int y, char piece, int dx, int dy) {
        List<Move> line = new ArrayList<>();
        while (inside(x, y) && BOARD[x][y] == piece) {
            line.add(new Move(x, y));
            x += dx;
            y += dy;
        }
        return line;
    }

    private static int countPieces(char piece) {
        int count = 0;
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                if (BOARD[i][j] == piece) {
                    count++;
                }
            }
        }
        return count;
    }

    private static Move findFirstPlayerMove() {
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                if (BOARD[i][j] == 'X') {
                    return new Move(i, j);
                }
            }
        }
        return new Move(-1, -1);
    }

    private static Move findAnyEmpty() {
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                if (BOARD[i][j] == '\0') {
                    BOARD[i][j] = 'O';
                    return new Move(i, j);
                }
            }
        }
        return new Move(-1, -1);
    }

    private static boolean inside(int x, int y) {
        return x >= 0 && x < SIZE && y >= 0 && y < SIZE;
    }

    private static Move placeAround(int x, int y) {
        List<Move> around = new ArrayList<>(List.of(
            new Move(x - 1, y - 1), new Move(x - 1, y), new Move(x - 1, y + 1),
            new Move(x, y - 1), new Move(x, y + 1),
            new Move(x + 1, y - 1), new Move(x + 1, y), new Move(x + 1, y + 1),
            new Move(x - 2, y - 2), new Move(x - 2, y - 1), new Move(x - 2, y), new Move(x - 2, y + 1), new Move(x - 2, y + 2),
            new Move(x - 1, y - 2), new Move(x - 1, y + 2),
            new Move(x, y - 2), new Move(x, y + 2),
            new Move(x + 1, y - 2), new Move(x + 1, y + 2),
            new Move(x + 2, y - 2), new Move(x + 2, y - 1), new Move(x + 2, y), new Move(x + 2, y + 1), new Move(x + 2, y + 2)
        ));
        Collections.shuffle(around, RANDOM);

        for (Move move : around) {
            if (inside(move.x(), move.y()) && BOARD[move.x()][move.y()] == '\0') {
                BOARD[move.x()][move.y()] = 'O';
                return move;
            }
        }
        return null;
    }

    private static Move blockPlayer() {
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                if (BOARD[i][j] == 'X') {
                    Move block = canBlock(i, j, 1, 0);
                    if (block != null) return block;
                    block = canBlock(i, j, 0, 1);
                    if (block != null) return block;
                    block = canBlock(i, j, 1, 1);
                    if (block != null) return block;
                    block = canBlock(i, j, 1, -1);
                    if (block != null) return block;
                }
            }
        }
        return null;
    }

    private static Move canBlock(int x, int y, int dx, int dy) {
        int count = 0;
        int startX = x;
        int startY = y;

        while (inside(startX, startY) && BOARD[startX][startY] == 'X') {
            count++;
            startX += dx;
            startY += dy;
        }

        boolean preEmpty = inside(x - dx, y - dy) && BOARD[x - dx][y - dy] == '\0';
        boolean postEmpty = inside(startX, startY) && BOARD[startX][startY] == '\0';

        if (count == 4 && (preEmpty || postEmpty)) {
            Move move = preEmpty ? new Move(x - dx, y - dy) : new Move(startX, startY);
            BOARD[move.x()][move.y()] = 'O';
            return move;
        }

        if (count == 3 && preEmpty && postEmpty) {
            Move move = new Move(x - dx, y - dy);
            BOARD[move.x()][move.y()] = 'O';
            return move;
        }
        return null;
    }

    private static Move expandBot() {
        List<Move> placed = new ArrayList<>();
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                if (BOARD[i][j] == 'O') {
                    placed.add(new Move(i, j));
                }
            }
        }

        for (Move p : placed) {
            List<Move> around = new ArrayList<>(List.of(
                new Move(p.x() - 1, p.y() - 1), new Move(p.x() - 1, p.y()), new Move(p.x() - 1, p.y() + 1),
                new Move(p.x(), p.y() - 1), new Move(p.x(), p.y() + 1),
                new Move(p.x() + 1, p.y() - 1), new Move(p.x() + 1, p.y()), new Move(p.x() + 1, p.y() + 1)
            ));
            Collections.shuffle(around, RANDOM);

            for (Move m : around) {
                if (inside(m.x(), m.y()) && BOARD[m.x()][m.y()] == '\0' && countSpace(m.x(), m.y()) >= 5) {
                    BOARD[m.x()][m.y()] = 'O';
                    return m;
                }
            }
        }
        return null;
    }

    private static Move createPattern() {
        List<Move> placed = new ArrayList<>();
        List<Move> empty = new ArrayList<>();

        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                if (BOARD[i][j] == 'O') {
                    placed.add(new Move(i, j));
                } else if (BOARD[i][j] == '\0') {
                    empty.add(new Move(i, j));
                }
            }
        }

        Move best = null;
        int maxAdjacent = -1;

        for (Move p : placed) {
            List<Move> around = List.of(
                new Move(p.x() - 1, p.y() - 1), new Move(p.x() - 1, p.y()), new Move(p.x() - 1, p.y() + 1),
                new Move(p.x(), p.y() - 1), new Move(p.x(), p.y() + 1),
                new Move(p.x() + 1, p.y() - 1), new Move(p.x() + 1, p.y()), new Move(p.x() + 1, p.y() + 1)
            );
            for (Move m : around) {
                if (inside(m.x(), m.y()) && BOARD[m.x()][m.y()] == '\0') {
                    int adjacent = countAdjacent(m.x(), m.y(), 'O');
                    if (countSpace(m.x(), m.y()) >= 5) {
                        BOARD[m.x()][m.y()] = 'O';
                        return m;
                    }
                    if (adjacent > maxAdjacent) {
                        maxAdjacent = adjacent;
                        best = m;
                    }
                }
            }
        }

        if (best != null) {
            BOARD[best.x()][best.y()] = 'O';
            return best;
        }

        if (!empty.isEmpty()) {
            Move random = empty.get(RANDOM.nextInt(empty.size()));
            BOARD[random.x()][random.y()] = 'O';
            return random;
        }

        return null;
    }

    private static int countAdjacent(int row, int col, char piece) {
        return Math.max(
            Math.max(countLine(row, col, piece, 1, 0), countLine(row, col, piece, 0, 1)),
            Math.max(countLine(row, col, piece, 1, 1), countLine(row, col, piece, 1, -1))
        );
    }

    private static int countLine(int row, int col, char piece, int dx, int dy) {
        int count = 0;
        for (int i = -4; i <= 4; i++) {
            int r = row + i * dx;
            int c = col + i * dy;
            if (inside(r, c) && BOARD[r][c] == piece) {
                count++;
            }
        }
        return count;
    }

    private static int countSpace(int x, int y) {
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        int max = 0;

        for (int[] dir : dirs) {
            int left = 0;
            int right = 0;

            int i = x + dir[0];
            int j = y + dir[1];
            while (inside(i, j) && BOARD[i][j] == '\0') {
                left++;
                i += dir[0];
                j += dir[1];
            }

            i = x - dir[0];
            j = y - dir[1];
            while (inside(i, j) && BOARD[i][j] == '\0') {
                right++;
                i -= dir[0];
                j -= dir[1];
            }

            max = Math.max(max, left + right);
        }

        return max;
    }

    public record Move(int x, int y) {
    }

    public record WinResult(boolean hasWin, List<Move> winLine) {
    }
}
