package com.game.hub.games.chess.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class ChessMoveRules {
    private static final int BOARD_SIZE = 8;

    private ChessMoveRules() {
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
        if (side == null || side.isBlank()) {
            return false;
        }
        if (board == null || board.length != BOARD_SIZE) {
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
        if (side == null || side.isBlank() || board == null || board.length != BOARD_SIZE) {
            return false;
        }
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                String piece = board[r][c];
                if (piece != null && piece.startsWith(side) && !getLegalMovesForPiece(board, r, c, side).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean isKingInCheck(String[][] board, String side) {
        Position king = findKing(board, side);
        if (king == null) {
            return false;
        }
        String attackerSide = "w".equalsIgnoreCase(side) ? "b" : "w";
        return isSquareAttacked(board, king.row, king.col, attackerSide);
    }

    static boolean hasKing(String[][] board, String side) {
        return findKing(board, side) != null;
    }

    private static Position findKing(String[][] board, String side) {
        if (board == null || side == null || side.isBlank()) {
            return null;
        }
        String target = side + "K";
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (target.equals(board[r][c])) {
                    return new Position(r, c);
                }
            }
        }
        return null;
    }

    private static boolean isSquareAttacked(String[][] board, int targetRow, int targetCol, String attackerSide) {
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
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
            if (!isKingInCheck(next, side)) {
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
            case 'P' -> pawnMoves(board, row, col, side, attacksOnly);
            case 'N' -> knightMoves(board, row, col, side);
            case 'B' -> slidingMoves(board, row, col, side, new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}});
            case 'R' -> slidingMoves(board, row, col, side, new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}});
            case 'Q' -> slidingMoves(board, row, col, side, new int[][]{
                {1, 1}, {1, -1}, {-1, 1}, {-1, -1}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}
            });
            case 'K' -> kingMoves(board, row, col, side);
            default -> List.of();
        };
    }

    private static List<Move> pawnMoves(String[][] board, int row, int col, String side, boolean attacksOnly) {
        List<Move> moves = new ArrayList<>();
        int dir = "w".equals(side) ? -1 : 1;
        int startRow = "w".equals(side) ? 6 : 1;

        for (int dc : new int[]{-1, 1}) {
            int tr = row + dir;
            int tc = col + dc;
            if (!inside(tr, tc)) continue;
            String target = board[tr][tc];
            if (attacksOnly) {
                moves.add(new Move(row, col, tr, tc, target != null && !target.isBlank()));
            } else if (target != null && !target.isBlank() && !target.startsWith(side)) {
                moves.add(new Move(row, col, tr, tc, true));
            }
        }

        if (attacksOnly) {
            return moves;
        }

        int oneStepRow = row + dir;
        if (inside(oneStepRow, col) && isEmpty(board[oneStepRow][col])) {
            moves.add(new Move(row, col, oneStepRow, col, false));
            int twoStepRow = row + dir * 2;
            if (row == startRow && inside(twoStepRow, col) && isEmpty(board[twoStepRow][col])) {
                moves.add(new Move(row, col, twoStepRow, col, false));
            }
        }
        return moves;
    }

    private static List<Move> knightMoves(String[][] board, int row, int col, String side) {
        int[][] deltas = {
            {2, 1}, {2, -1}, {-2, 1}, {-2, -1},
            {1, 2}, {1, -2}, {-1, 2}, {-1, -2}
        };
        List<Move> moves = new ArrayList<>();
        for (int[] delta : deltas) {
            int tr = row + delta[0];
            int tc = col + delta[1];
            if (!inside(tr, tc)) continue;
            String target = board[tr][tc];
            if (isEmpty(target) || !target.startsWith(side)) {
                moves.add(new Move(row, col, tr, tc, !isEmpty(target)));
            }
        }
        return moves;
    }

    private static List<Move> kingMoves(String[][] board, int row, int col, String side) {
        List<Move> moves = new ArrayList<>();
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;
                int tr = row + dr;
                int tc = col + dc;
                if (!inside(tr, tc)) continue;
                String target = board[tr][tc];
                if (isEmpty(target) || !target.startsWith(side)) {
                    moves.add(new Move(row, col, tr, tc, !isEmpty(target)));
                }
            }
        }
        return moves;
    }

    private static List<Move> slidingMoves(String[][] board, int row, int col, String side, int[][] directions) {
        List<Move> moves = new ArrayList<>();
        for (int[] dir : directions) {
            int tr = row + dir[0];
            int tc = col + dir[1];
            while (inside(tr, tc)) {
                String target = board[tr][tc];
                if (isEmpty(target)) {
                    moves.add(new Move(row, col, tr, tc, false));
                } else {
                    if (!target.startsWith(side)) {
                        moves.add(new Move(row, col, tr, tc, true));
                    }
                    break;
                }
                tr += dir[0];
                tc += dir[1];
            }
        }
        return moves;
    }

    private static String[][] applyMoveOnBoard(String[][] board, Move move) {
        String[][] next = cloneBoard(board);
        String piece = next[move.fromRow][move.fromCol];
        next[move.fromRow][move.fromCol] = null;
        if (piece != null && piece.length() >= 2 && piece.charAt(1) == 'P'
            && (move.toRow == 0 || move.toRow == BOARD_SIZE - 1)) {
            next[move.toRow][move.toCol] = piece.substring(0, 1) + "Q";
        } else {
            next[move.toRow][move.toCol] = piece;
        }
        return next;
    }

    private static String[][] cloneBoard(String[][] board) {
        String[][] copy = new String[BOARD_SIZE][BOARD_SIZE];
        for (int i = 0; i < BOARD_SIZE; i++) {
            copy[i] = (board != null && i < board.length && board[i] != null)
                ? Arrays.copyOf(board[i], BOARD_SIZE)
                : new String[BOARD_SIZE];
        }
        return copy;
    }

    private static boolean inside(int row, int col) {
        return row >= 0 && col >= 0 && row < BOARD_SIZE && col < BOARD_SIZE;
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isBlank();
    }

    private record Position(int row, int col) {
    }

    private record Move(int fromRow, int fromCol, int toRow, int toCol, boolean capture) {
    }
}
