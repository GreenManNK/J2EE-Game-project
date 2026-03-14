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
        return isLegalMove(board, fromRow, fromCol, toRow, toCol, side, MoveContext.empty());
    }

    static boolean isLegalMove(String[][] board,
                               int fromRow,
                               int fromCol,
                               int toRow,
                               int toCol,
                               String side,
                               MoveContext context) {
        return resolveLegalMove(board, fromRow, fromCol, toRow, toCol, side, context) != null;
    }

    static ResolvedMove resolveLegalMove(String[][] board,
                                         int fromRow,
                                         int fromCol,
                                         int toRow,
                                         int toCol,
                                         String side,
                                         MoveContext context) {
        if (!inside(fromRow, fromCol) || !inside(toRow, toCol)) {
            return null;
        }
        if (side == null || side.isBlank()) {
            return null;
        }
        if (board == null || board.length != BOARD_SIZE) {
            return null;
        }
        String piece = board[fromRow][fromCol];
        if (piece == null || piece.isBlank() || !piece.startsWith(side)) {
            return null;
        }
        return getLegalMovesForPiece(board, fromRow, fromCol, side, normalizeContext(context)).stream()
            .filter(move -> move.toRow() == toRow && move.toCol() == toCol)
            .findFirst()
            .orElse(null);
    }

    static boolean hasAnyLegalMove(String[][] board, String side) {
        return hasAnyLegalMove(board, side, MoveContext.empty());
    }

    static boolean hasAnyLegalMove(String[][] board, String side, MoveContext context) {
        if (side == null || side.isBlank() || board == null || board.length != BOARD_SIZE) {
            return false;
        }
        MoveContext resolvedContext = normalizeContext(context);
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                String piece = board[r][c];
                if (piece != null && piece.startsWith(side)
                    && !getLegalMovesForPiece(board, r, c, side, resolvedContext).isEmpty()) {
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
        return isSquareAttacked(board, king.row(), king.col(), attackerSide);
    }

    static boolean hasKing(String[][] board, String side) {
        return findKing(board, side) != null;
    }

    private static MoveContext normalizeContext(MoveContext context) {
        return context == null ? MoveContext.empty() : context;
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
                List<ResolvedMove> attacks = getPseudoMoves(board, r, c, piece, true, MoveContext.empty());
                for (ResolvedMove move : attacks) {
                    if (move.toRow() == targetRow && move.toCol() == targetCol) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static List<ResolvedMove> getLegalMovesForPiece(String[][] board,
                                                            int row,
                                                            int col,
                                                            String side,
                                                            MoveContext context) {
        String piece = board[row][col];
        if (piece == null || !piece.startsWith(side)) {
            return List.of();
        }
        List<ResolvedMove> pseudo = getPseudoMoves(board, row, col, piece, false, context);
        List<ResolvedMove> legal = new ArrayList<>(pseudo.size());
        for (ResolvedMove move : pseudo) {
            String[][] next = applyMoveOnBoard(board, move);
            if (!isKingInCheck(next, side)) {
                legal.add(move);
            }
        }
        return legal;
    }

    private static List<ResolvedMove> getPseudoMoves(String[][] board,
                                                     int row,
                                                     int col,
                                                     String piece,
                                                     boolean attacksOnly,
                                                     MoveContext context) {
        if (piece == null || piece.length() < 2) {
            return List.of();
        }
        String side = piece.substring(0, 1);
        char type = piece.charAt(1);
        return switch (type) {
            case 'P' -> pawnMoves(board, row, col, side, attacksOnly, context);
            case 'N' -> knightMoves(board, row, col, side);
            case 'B' -> slidingMoves(board, row, col, side, new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}});
            case 'R' -> slidingMoves(board, row, col, side, new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}});
            case 'Q' -> slidingMoves(board, row, col, side, new int[][]{
                {1, 1}, {1, -1}, {-1, 1}, {-1, -1}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}
            });
            case 'K' -> kingMoves(board, row, col, side, attacksOnly, context);
            default -> List.of();
        };
    }

    private static List<ResolvedMove> pawnMoves(String[][] board,
                                                int row,
                                                int col,
                                                String side,
                                                boolean attacksOnly,
                                                MoveContext context) {
        List<ResolvedMove> moves = new ArrayList<>();
        int dir = "w".equals(side) ? -1 : 1;
        int startRow = "w".equals(side) ? 6 : 1;

        for (int dc : new int[]{-1, 1}) {
            int tr = row + dir;
            int tc = col + dc;
            if (!inside(tr, tc)) {
                continue;
            }
            String target = board[tr][tc];
            if (attacksOnly) {
                moves.add(ResolvedMove.standard(row, col, tr, tc, !isEmpty(target)));
                continue;
            }
            if (!isEmpty(target) && !target.startsWith(side)) {
                moves.add(ResolvedMove.standard(row, col, tr, tc, true));
                continue;
            }
            if (context.matchesEnPassant(tr, tc)) {
                String capturedPawn = board[row][tc];
                if (!isEmpty(capturedPawn) && capturedPawn.length() >= 2
                    && capturedPawn.charAt(1) == 'P'
                    && !capturedPawn.startsWith(side)) {
                    moves.add(ResolvedMove.enPassant(row, col, tr, tc, row, tc));
                }
            }
        }

        if (attacksOnly) {
            return moves;
        }

        int oneStepRow = row + dir;
        if (inside(oneStepRow, col) && isEmpty(board[oneStepRow][col])) {
            moves.add(ResolvedMove.standard(row, col, oneStepRow, col, false));
            int twoStepRow = row + dir * 2;
            if (row == startRow && inside(twoStepRow, col) && isEmpty(board[twoStepRow][col])) {
                moves.add(ResolvedMove.standard(row, col, twoStepRow, col, false));
            }
        }
        return moves;
    }

    private static List<ResolvedMove> knightMoves(String[][] board, int row, int col, String side) {
        int[][] deltas = {
            {2, 1}, {2, -1}, {-2, 1}, {-2, -1},
            {1, 2}, {1, -2}, {-1, 2}, {-1, -2}
        };
        List<ResolvedMove> moves = new ArrayList<>();
        for (int[] delta : deltas) {
            int tr = row + delta[0];
            int tc = col + delta[1];
            if (!inside(tr, tc)) {
                continue;
            }
            String target = board[tr][tc];
            if (isEmpty(target) || !target.startsWith(side)) {
                moves.add(ResolvedMove.standard(row, col, tr, tc, !isEmpty(target)));
            }
        }
        return moves;
    }

    private static List<ResolvedMove> kingMoves(String[][] board,
                                                int row,
                                                int col,
                                                String side,
                                                boolean attacksOnly,
                                                MoveContext context) {
        List<ResolvedMove> moves = new ArrayList<>();
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) {
                    continue;
                }
                int tr = row + dr;
                int tc = col + dc;
                if (!inside(tr, tc)) {
                    continue;
                }
                String target = board[tr][tc];
                if (isEmpty(target) || !target.startsWith(side)) {
                    moves.add(ResolvedMove.standard(row, col, tr, tc, !isEmpty(target)));
                }
            }
        }
        if (attacksOnly) {
            return moves;
        }

        int homeRow = "w".equals(side) ? 7 : 0;
        if (row != homeRow || col != 4) {
            return moves;
        }
        String attackerSide = "w".equals(side) ? "b" : "w";
        if (isSquareAttacked(board, row, col, attackerSide)) {
            return moves;
        }
        if (canCastleKingSide(board, row, side, context, attackerSide)) {
            moves.add(ResolvedMove.castleKingSide(row, col));
        }
        if (canCastleQueenSide(board, row, side, context, attackerSide)) {
            moves.add(ResolvedMove.castleQueenSide(row, col));
        }
        return moves;
    }

    private static boolean canCastleKingSide(String[][] board,
                                             int row,
                                             String side,
                                             MoveContext context,
                                             String attackerSide) {
        if (!context.canCastleKingSide(side)) {
            return false;
        }
        String rook = board[row][7];
        if (!(side + "R").equals(rook)) {
            return false;
        }
        if (!isEmpty(board[row][5]) || !isEmpty(board[row][6])) {
            return false;
        }
        return !isSquareAttacked(board, row, 5, attackerSide) && !isSquareAttacked(board, row, 6, attackerSide);
    }

    private static boolean canCastleQueenSide(String[][] board,
                                              int row,
                                              String side,
                                              MoveContext context,
                                              String attackerSide) {
        if (!context.canCastleQueenSide(side)) {
            return false;
        }
        String rook = board[row][0];
        if (!(side + "R").equals(rook)) {
            return false;
        }
        if (!isEmpty(board[row][1]) || !isEmpty(board[row][2]) || !isEmpty(board[row][3])) {
            return false;
        }
        return !isSquareAttacked(board, row, 3, attackerSide) && !isSquareAttacked(board, row, 2, attackerSide);
    }

    private static List<ResolvedMove> slidingMoves(String[][] board, int row, int col, String side, int[][] directions) {
        List<ResolvedMove> moves = new ArrayList<>();
        for (int[] dir : directions) {
            int tr = row + dir[0];
            int tc = col + dir[1];
            while (inside(tr, tc)) {
                String target = board[tr][tc];
                if (isEmpty(target)) {
                    moves.add(ResolvedMove.standard(row, col, tr, tc, false));
                } else {
                    if (!target.startsWith(side)) {
                        moves.add(ResolvedMove.standard(row, col, tr, tc, true));
                    }
                    break;
                }
                tr += dir[0];
                tc += dir[1];
            }
        }
        return moves;
    }

    private static String[][] applyMoveOnBoard(String[][] board, ResolvedMove move) {
        String[][] next = cloneBoard(board);
        String piece = next[move.fromRow()][move.fromCol()];
        next[move.fromRow()][move.fromCol()] = null;
        if (move.isEnPassant()) {
            next[move.capturedRow()][move.capturedCol()] = null;
        }
        String movedPiece = piece;
        if (piece != null && piece.length() >= 2 && piece.charAt(1) == 'P'
            && (move.toRow() == 0 || move.toRow() == BOARD_SIZE - 1)) {
            movedPiece = piece.substring(0, 1) + "Q";
        }
        next[move.toRow()][move.toCol()] = movedPiece;
        if (move.isCastleKingSide()) {
            next[move.toRow()][5] = next[move.toRow()][7];
            next[move.toRow()][7] = null;
        } else if (move.isCastleQueenSide()) {
            next[move.toRow()][3] = next[move.toRow()][0];
            next[move.toRow()][0] = null;
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

    static record MoveContext(boolean whiteKingSideCastle,
                              boolean whiteQueenSideCastle,
                              boolean blackKingSideCastle,
                              boolean blackQueenSideCastle,
                              Integer enPassantRow,
                              Integer enPassantCol) {
        static MoveContext empty() {
            return new MoveContext(false, false, false, false, null, null);
        }

        boolean canCastleKingSide(String side) {
            return "w".equalsIgnoreCase(side) ? whiteKingSideCastle : blackKingSideCastle;
        }

        boolean canCastleQueenSide(String side) {
            return "w".equalsIgnoreCase(side) ? whiteQueenSideCastle : blackQueenSideCastle;
        }

        boolean matchesEnPassant(int row, int col) {
            return enPassantRow != null
                && enPassantCol != null
                && enPassantRow == row
                && enPassantCol == col;
        }
    }

    static record ResolvedMove(int fromRow,
                               int fromCol,
                               int toRow,
                               int toCol,
                               boolean capture,
                               boolean enPassant,
                               int capturedRow,
                               int capturedCol,
                               boolean castleKingSide,
                               boolean castleQueenSide) {
        static ResolvedMove standard(int fromRow, int fromCol, int toRow, int toCol, boolean capture) {
            return new ResolvedMove(fromRow, fromCol, toRow, toCol, capture, false, toRow, toCol, false, false);
        }

        static ResolvedMove enPassant(int fromRow, int fromCol, int toRow, int toCol, int capturedRow, int capturedCol) {
            return new ResolvedMove(fromRow, fromCol, toRow, toCol, true, true, capturedRow, capturedCol, false, false);
        }

        static ResolvedMove castleKingSide(int row, int col) {
            return new ResolvedMove(row, col, row, col + 2, false, false, row, col + 2, true, false);
        }

        static ResolvedMove castleQueenSide(int row, int col) {
            return new ResolvedMove(row, col, row, col - 2, false, false, row, col - 2, false, true);
        }

        boolean isEnPassant() {
            return enPassant;
        }

        boolean isCastleKingSide() {
            return castleKingSide;
        }

        boolean isCastleQueenSide() {
            return castleQueenSide;
        }
    }
}
