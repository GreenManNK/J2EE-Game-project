package com.caro.game.xiangqi.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class XiangqiMoveRules {
    private static final int ROWS = 10;
    private static final int COLS = 9;

    private XiangqiMoveRules() {
    }

    static boolean isLegalMove(String[][] board,
                               int fromRow,
                               int fromCol,
                               int toRow,
                               int toCol,
                               String side) {
        if (!inside(fromRow, fromCol) || !inside(toRow, toCol)) {
            return false;
        }
        if (side == null || side.isBlank() || board == null || board.length != ROWS) {
            return false;
        }
        String piece = board[fromRow][fromCol];
        if (piece == null || piece.isBlank() || !piece.startsWith(side)) {
            return false;
        }
        return getLegalMovesForPiece(board, fromRow, fromCol, side).stream()
            .anyMatch(move -> move.toRow == toRow && move.toCol == toCol);
    }

    static boolean hasAnyLegalMove(String[][] board, String side) {
        if (board == null || side == null || side.isBlank()) {
            return false;
        }
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                String piece = board[r][c];
                if (piece != null && piece.startsWith(side) && !getLegalMovesForPiece(board, r, c, side).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean isGeneralInCheck(String[][] board, String side) {
        Position general = findGeneral(board, side);
        if (general == null) {
            return true;
        }
        String opponent = "r".equalsIgnoreCase(side) ? "b" : "r";
        return isSquareAttacked(board, general.row, general.col, opponent);
    }

    static boolean hasGeneral(String[][] board, String side) {
        return findGeneral(board, side) != null;
    }

    private static Position findGeneral(String[][] board, String side) {
        if (board == null || side == null || side.isBlank()) {
            return null;
        }
        String target = side + "G";
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (target.equals(board[r][c])) {
                    return new Position(r, c);
                }
            }
        }
        return null;
    }

    private static boolean isSquareAttacked(String[][] board, int targetRow, int targetCol, String attackerSide) {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                String piece = board[r][c];
                if (piece == null || !piece.startsWith(attackerSide)) {
                    continue;
                }
                List<Move> attacks = getPseudoMoves(board, r, c, piece, true);
                for (Move move : attacks) {
                    if (move.toRow == targetRow && move.toCol == targetCol) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static List<Move> getLegalMovesForPiece(String[][] board, int row, int col, String side) {
        String piece = board[row][col];
        if (piece == null || !piece.startsWith(side)) {
            return List.of();
        }
        List<Move> pseudo = getPseudoMoves(board, row, col, piece, false);
        List<Move> legal = new ArrayList<>(pseudo.size());
        for (Move move : pseudo) {
            String[][] next = applyMoveOnBoard(board, move);
            if (!isGeneralInCheck(next, side)) {
                legal.add(move);
            }
        }
        return legal;
    }

    private static List<Move> getPseudoMoves(String[][] board, int row, int col, String piece, boolean attacksOnly) {
        if (piece == null || piece.length() < 2) {
            return List.of();
        }
        String side = piece.substring(0, 1);
        char type = piece.charAt(1);
        return switch (type) {
            case 'R' -> rookMoves(board, row, col, side);
            case 'C' -> cannonMoves(board, row, col, side, attacksOnly);
            case 'H' -> horseMoves(board, row, col, side);
            case 'E' -> elephantMoves(board, row, col, side);
            case 'A' -> advisorMoves(board, row, col, side);
            case 'G' -> generalMoves(board, row, col, side, attacksOnly);
            case 'P' -> pawnMoves(board, row, col, side);
            default -> List.of();
        };
    }

    private static List<Move> rookMoves(String[][] board, int row, int col, String side) {
        return linearMoves(board, row, col, side, new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}});
    }

    private static List<Move> cannonMoves(String[][] board, int row, int col, String side, boolean attacksOnly) {
        List<Move> moves = new ArrayList<>();
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] dir : dirs) {
            int r = row + dir[0];
            int c = col + dir[1];
            boolean seenScreen = false;
            while (inside(r, c)) {
                String target = board[r][c];
                if (!seenScreen) {
                    if (isEmpty(target)) {
                        if (!attacksOnly) {
                            moves.add(new Move(row, col, r, c, false));
                        }
                    } else {
                        seenScreen = true;
                    }
                } else if (!isEmpty(target)) {
                    if (attacksOnly || !target.startsWith(side)) {
                        if (!target.startsWith(side)) {
                            moves.add(new Move(row, col, r, c, true));
                        } else if (attacksOnly) {
                            moves.add(new Move(row, col, r, c, false));
                        }
                    }
                    break;
                }
                r += dir[0];
                c += dir[1];
            }
        }
        return moves;
    }

    private static List<Move> horseMoves(String[][] board, int row, int col, String side) {
        List<Move> moves = new ArrayList<>();
        int[][] legSteps = {
            {1, 0, 2, 1}, {1, 0, 2, -1},
            {-1, 0, -2, 1}, {-1, 0, -2, -1},
            {0, 1, 1, 2}, {0, 1, -1, 2},
            {0, -1, 1, -2}, {0, -1, -1, -2}
        };
        for (int[] p : legSteps) {
            int legRow = row + p[0];
            int legCol = col + p[1];
            if (!inside(legRow, legCol) || !isEmpty(board[legRow][legCol])) {
                continue;
            }
            int tr = row + p[2];
            int tc = col + p[3];
            if (!inside(tr, tc)) {
                continue;
            }
            String target = board[tr][tc];
            if (isEmpty(target) || !target.startsWith(side)) {
                moves.add(new Move(row, col, tr, tc, !isEmpty(target)));
            }
        }
        return moves;
    }

    private static List<Move> elephantMoves(String[][] board, int row, int col, String side) {
        List<Move> moves = new ArrayList<>();
        int[][] deltas = {{2, 2}, {2, -2}, {-2, 2}, {-2, -2}};
        for (int[] delta : deltas) {
            int tr = row + delta[0];
            int tc = col + delta[1];
            int eyeRow = row + delta[0] / 2;
            int eyeCol = col + delta[1] / 2;
            if (!inside(tr, tc)) continue;
            if (!isEmpty(board[eyeRow][eyeCol])) continue;
            if (!staysOnOwnSideRiver(tr, side)) continue;
            String target = board[tr][tc];
            if (isEmpty(target) || !target.startsWith(side)) {
                moves.add(new Move(row, col, tr, tc, !isEmpty(target)));
            }
        }
        return moves;
    }

    private static List<Move> advisorMoves(String[][] board, int row, int col, String side) {
        List<Move> moves = new ArrayList<>();
        int[][] deltas = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int[] delta : deltas) {
            int tr = row + delta[0];
            int tc = col + delta[1];
            if (!inside(tr, tc) || !insidePalace(tr, tc, side)) continue;
            String target = board[tr][tc];
            if (isEmpty(target) || !target.startsWith(side)) {
                moves.add(new Move(row, col, tr, tc, !isEmpty(target)));
            }
        }
        return moves;
    }

    private static List<Move> generalMoves(String[][] board, int row, int col, String side, boolean attacksOnly) {
        List<Move> moves = new ArrayList<>();
        int[][] deltas = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] delta : deltas) {
            int tr = row + delta[0];
            int tc = col + delta[1];
            if (!inside(tr, tc) || !insidePalace(tr, tc, side)) continue;
            String target = board[tr][tc];
            if (isEmpty(target) || !target.startsWith(side)) {
                moves.add(new Move(row, col, tr, tc, !isEmpty(target)));
            }
        }

        // Flying general attack/capture on same file if clear.
        int step = "r".equals(side) ? -1 : 1;
        int r = row + step;
        while (inside(r, col)) {
            String target = board[r][col];
            if (!isEmpty(target)) {
                if (target.equals("r".equals(side) ? "bG" : "rG")) {
                    moves.add(new Move(row, col, r, col, true));
                }
                break;
            }
            r += step;
        }
        return moves;
    }

    private static List<Move> pawnMoves(String[][] board, int row, int col, String side) {
        List<Move> moves = new ArrayList<>();
        List<int[]> dirs = new ArrayList<>(3);
        dirs.add("r".equals(side) ? new int[]{-1, 0} : new int[]{1, 0}); // forward
        if (crossedRiver(row, side)) {
            dirs.add(new int[]{0, 1});
            dirs.add(new int[]{0, -1});
        }
        for (int[] dir : dirs) {
            int tr = row + dir[0];
            int tc = col + dir[1];
            if (!inside(tr, tc)) continue;
            String target = board[tr][tc];
            if (isEmpty(target) || !target.startsWith(side)) {
                moves.add(new Move(row, col, tr, tc, !isEmpty(target)));
            }
        }
        return moves;
    }

    private static List<Move> linearMoves(String[][] board, int row, int col, String side, int[][] dirs) {
        List<Move> moves = new ArrayList<>();
        for (int[] dir : dirs) {
            int r = row + dir[0];
            int c = col + dir[1];
            while (inside(r, c)) {
                String target = board[r][c];
                if (isEmpty(target)) {
                    moves.add(new Move(row, col, r, c, false));
                } else {
                    if (!target.startsWith(side)) {
                        moves.add(new Move(row, col, r, c, true));
                    }
                    break;
                }
                r += dir[0];
                c += dir[1];
            }
        }
        return moves;
    }

    private static String[][] applyMoveOnBoard(String[][] board, Move move) {
        String[][] next = cloneBoard(board);
        next[move.toRow][move.toCol] = next[move.fromRow][move.fromCol];
        next[move.fromRow][move.fromCol] = null;
        return next;
    }

    private static String[][] cloneBoard(String[][] board) {
        String[][] copy = new String[ROWS][COLS];
        for (int i = 0; i < ROWS; i++) {
            copy[i] = (board != null && i < board.length && board[i] != null)
                ? Arrays.copyOf(board[i], COLS)
                : new String[COLS];
        }
        return copy;
    }

    private static boolean inside(int row, int col) {
        return row >= 0 && row < ROWS && col >= 0 && col < COLS;
    }

    private static boolean insidePalace(int row, int col, String side) {
        if (col < 3 || col > 5) return false;
        return "r".equals(side) ? (row >= 7 && row <= 9) : (row >= 0 && row <= 2);
    }

    private static boolean staysOnOwnSideRiver(int row, String side) {
        return "r".equals(side) ? row >= 5 : row <= 4;
    }

    private static boolean crossedRiver(int row, String side) {
        return "r".equals(side) ? row <= 4 : row >= 5;
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isBlank();
    }

    private record Position(int row, int col) {
    }

    private record Move(int fromRow, int fromCol, int toRow, int toCol, boolean capture) {
    }
}
