package com.game.hub.chess.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChessOnlineRoomService {
    private static final int BOARD_SIZE = 8;
    private static final int PLAYER_LIMIT = 2;
    private static final String STATUS_WAITING = "WAITING";
    private static final String STATUS_PLAYING = "PLAYING";
    private static final String STATUS_GAME_OVER = "GAME_OVER";

    private final Map<String, RoomState> rooms = new ConcurrentHashMap<>();

    public synchronized JoinResult joinRoom(String roomId,
                                            String userId,
                                            String displayName,
                                            String avatarPath) {
        String normalizedRoomId = normalize(roomId);
        String normalizedUserId = normalize(userId);
        if (normalizedRoomId == null || normalizedUserId == null) {
            return new JoinResult(false, null, null, "Invalid room or user");
        }

        RoomState room = rooms.computeIfAbsent(normalizedRoomId, RoomState::new);
        PlayerSeatState existing = room.players.get(normalizedUserId);
        if (existing != null) {
            if (displayName != null && !displayName.isBlank()) {
                existing.displayName = displayName.trim();
            }
            if (avatarPath != null && !avatarPath.isBlank()) {
                existing.avatarPath = avatarPath.trim();
            }
            return new JoinResult(true, existing.color, snapshotOf(room), null);
        }

        if (room.players.size() >= PLAYER_LIMIT) {
            return new JoinResult(false, null, snapshotOf(room), "Room is full");
        }

        String assignedColor = room.players.values().stream().anyMatch(player -> "w".equals(player.color)) ? "b" : "w";
        room.players.put(normalizedUserId, new PlayerSeatState(
            normalizedUserId,
            displayName == null || displayName.isBlank() ? normalizedUserId : displayName.trim(),
            avatarPath == null || avatarPath.isBlank() ? "" : avatarPath.trim(),
            assignedColor
        ));

        if (room.players.size() == PLAYER_LIMIT) {
            resetBoardForCurrentPlayers(room, "Da du 2 nguoi - bat dau van moi");
        } else {
            room.status = STATUS_WAITING;
            room.statusMessage = "Dang cho doi thu vao phong";
            room.currentTurnUserId = null;
            room.currentTurnColor = "w";
            if (room.board == null) {
                room.board = createInitialBoard();
            }
        }

        return new JoinResult(true, assignedColor, snapshotOf(room), null);
    }

    public synchronized ActionResult move(String roomId,
                                          String userId,
                                          int fromRow,
                                          int fromCol,
                                          int toRow,
                                          int toCol,
                                          String promotion) {
        RoomState room = rooms.get(normalize(roomId));
        String normalizedUserId = normalize(userId);
        if (room == null || normalizedUserId == null) {
            return ActionResult.error("Room not found");
        }
        PlayerSeatState player = room.players.get(normalizedUserId);
        if (player == null) {
            return ActionResult.error("Player does not belong to room");
        }
        if (room.players.size() < PLAYER_LIMIT) {
            return ActionResult.error("Waiting for opponent");
        }
        if (STATUS_GAME_OVER.equals(room.status)) {
            return ActionResult.error("Game already ended");
        }
        if (!STATUS_PLAYING.equals(room.status)) {
            room.status = STATUS_PLAYING;
        }
        if (room.currentTurnUserId == null) {
            room.currentTurnUserId = userIdForColor(room, "w");
            room.currentTurnColor = "w";
        }
        if (!normalizedUserId.equals(room.currentTurnUserId)) {
            return ActionResult.error("Not your turn");
        }
        if (!inside(fromRow, fromCol) || !inside(toRow, toCol)) {
            return ActionResult.error("Invalid position");
        }
        if (fromRow == toRow && fromCol == toCol) {
            return ActionResult.error("Move requires different source and target");
        }

        String[][] board = room.board;
        if (board == null) {
            room.board = createInitialBoard();
            board = room.board;
        }
        String piece = board[fromRow][fromCol];
        if (piece == null || piece.isBlank()) {
            return ActionResult.error("No piece at source");
        }
        if (!piece.startsWith(player.color)) {
            return ActionResult.error("Selected piece does not belong to you");
        }
        String target = board[toRow][toCol];
        if (target != null && !target.isBlank() && target.startsWith(player.color)) {
            return ActionResult.error("Cannot capture your own piece");
        }
        if (!ChessMoveRules.isLegalMove(board, fromRow, fromCol, toRow, toCol, player.color)) {
            return ActionResult.error("Illegal move");
        }

        String movedPiece = piece;
        String requestedPromotion = normalizePromotion(promotion);
        if (isPawnPromotion(piece, toRow)) {
            movedPiece = piece.substring(0, 1) + requestedPromotion;
        }

        board[fromRow][fromCol] = null;
        board[toRow][toCol] = movedPiece;

        String notation = buildNotation(piece, fromRow, fromCol, toRow, toCol, target, movedPiece, requestedPromotion);
        room.moveHistory.add(notation);
        room.lastMove = new LastMoveState(fromRow, fromCol, toRow, toCol, piece, target, movedPiece, notation);

        String nextColor = "w".equals(player.color) ? "b" : "w";
        room.currentTurnColor = nextColor;
        room.currentTurnUserId = userIdForColor(room, nextColor);
        room.status = STATUS_PLAYING;

        String winnerColor = player.color;
        if (!ChessMoveRules.hasKing(board, nextColor)) {
            room.status = STATUS_GAME_OVER;
            room.currentTurnUserId = null;
            room.currentTurnColor = winnerColor;
            room.statusMessage = colorLabel(winnerColor) + " da an Vua va chien thang!";
            return ActionResult.ok("MOVE", snapshotOf(room));
        }

        boolean opponentInCheck = ChessMoveRules.isKingInCheck(board, nextColor);
        boolean opponentHasMove = ChessMoveRules.hasAnyLegalMove(board, nextColor);
        if (!opponentHasMove && opponentInCheck) {
            room.status = STATUS_GAME_OVER;
            room.currentTurnUserId = null;
            room.currentTurnColor = winnerColor;
            room.statusMessage = "Chieu het. " + colorLabel(winnerColor) + " thang.";
            return ActionResult.ok("MOVE", snapshotOf(room));
        }
        if (!opponentHasMove) {
            room.status = STATUS_GAME_OVER;
            room.currentTurnUserId = null;
            room.statusMessage = "Hoa co (stalemate)";
            return ActionResult.ok("MOVE", snapshotOf(room));
        }

        room.statusMessage = room.currentTurnUserId == null
            ? "Dang cho doi thu ket noi lai"
            : (opponentInCheck ? (colorLabel(nextColor) + " dang bi chieu!") : "Nuoc di hop le");

        return ActionResult.ok("MOVE", snapshotOf(room));
    }

    public synchronized ActionResult surrenderGame(String roomId, String userId) {
        RoomState room = rooms.get(normalize(roomId));
        String normalizedUserId = normalize(userId);
        if (room == null || normalizedUserId == null) {
            return ActionResult.error("Room not found");
        }
        PlayerSeatState player = room.players.get(normalizedUserId);
        if (player == null) {
            return ActionResult.error("Player does not belong to room");
        }
        if (room.players.size() < PLAYER_LIMIT) {
            return ActionResult.error("Waiting for opponent");
        }
        if (STATUS_GAME_OVER.equals(room.status)) {
            return ActionResult.error("Game already ended");
        }

        String winnerColor = "w".equals(player.color) ? "b" : "w";
        String winnerUserId = userIdForColor(room, winnerColor);
        PlayerSeatState winner = winnerUserId == null ? null : room.players.get(winnerUserId);
        if (winner == null) {
            return ActionResult.error("Opponent not found");
        }

        String loserName = displayNameOf(player);
        String winnerName = displayNameOf(winner);
        String loserColorLabel = "w".equals(player.color) ? "Trang" : "Den";

        room.status = STATUS_GAME_OVER;
        room.statusMessage = loserName + " da dau hang. " + winnerName + " thang.";
        room.currentTurnUserId = null;
        room.currentTurnColor = winnerColor;
        room.moveHistory.add(loserColorLabel + " dau hang");

        return ActionResult.ok("SURRENDER", snapshotOf(room));
    }

    public synchronized ActionResult resetGame(String roomId, String userId) {
        RoomState room = rooms.get(normalize(roomId));
        String normalizedUserId = normalize(userId);
        if (room == null || normalizedUserId == null) {
            return ActionResult.error("Room not found");
        }
        if (!room.players.containsKey(normalizedUserId)) {
            return ActionResult.error("Player does not belong to room");
        }
        resetBoardForCurrentPlayers(room, room.players.size() >= PLAYER_LIMIT
            ? "Van moi da san sang"
            : "Dang cho doi thu vao phong");
        return ActionResult.ok("RESET", snapshotOf(room));
    }

    public synchronized void leaveRoom(String roomId, String userId) {
        RoomState room = rooms.get(normalize(roomId));
        String normalizedUserId = normalize(userId);
        if (room == null || normalizedUserId == null) {
            return;
        }
        room.players.remove(normalizedUserId);
        if (room.players.isEmpty()) {
            rooms.remove(room.roomId);
            return;
        }
        reassignColors(room);
        resetBoardForCurrentPlayers(room, "Nguoi choi da roi phong - dang cho doi thu moi");
    }

    public synchronized RoomSnapshot roomSnapshot(String roomId) {
        RoomState room = rooms.get(normalize(roomId));
        return room == null ? null : snapshotOf(room);
    }

    public synchronized List<RoomListRow> availableRooms() {
        return rooms.values().stream()
            .filter(room -> room.players.size() < PLAYER_LIMIT)
            .sorted(Comparator.comparing(room -> room.roomId))
            .map(room -> new RoomListRow(
                room.roomId,
                room.players.size(),
                PLAYER_LIMIT,
                room.players.size() < PLAYER_LIMIT ? "Dang cho doi thu" : "Dang choi"
            ))
            .toList();
    }

    private void resetBoardForCurrentPlayers(RoomState room, String statusMessage) {
        room.board = createInitialBoard();
        room.moveHistory.clear();
        room.lastMove = null;
        if (room.players.size() >= PLAYER_LIMIT) {
            room.status = STATUS_PLAYING;
            room.currentTurnColor = "w";
            room.currentTurnUserId = userIdForColor(room, "w");
            room.statusMessage = statusMessage == null || statusMessage.isBlank()
                ? "Da san sang choi"
                : statusMessage;
        } else {
            room.status = STATUS_WAITING;
            room.currentTurnColor = "w";
            room.currentTurnUserId = null;
            room.statusMessage = statusMessage == null || statusMessage.isBlank()
                ? "Dang cho doi thu vao phong"
                : statusMessage;
        }
    }

    private void reassignColors(RoomState room) {
        List<PlayerSeatState> players = new ArrayList<>(room.players.values());
        players.sort(Comparator.comparing(player -> player.userId));
        room.players.clear();
        for (int i = 0; i < players.size(); i++) {
            PlayerSeatState player = players.get(i);
            player.color = (i == 0) ? "w" : "b";
            room.players.put(player.userId, player);
        }
    }

    private RoomSnapshot snapshotOf(RoomState room) {
        String[][] boardCopy = new String[BOARD_SIZE][BOARD_SIZE];
        for (int i = 0; i < BOARD_SIZE; i++) {
            boardCopy[i] = Arrays.copyOf(room.board[i], BOARD_SIZE);
        }
        List<PlayerSnapshot> players = room.players.values().stream()
            .sorted(Comparator.comparing(player -> player.color))
            .map(player -> new PlayerSnapshot(player.userId, player.displayName, player.avatarPath, player.color))
            .toList();

        LastMoveSnapshot lastMove = room.lastMove == null ? null : new LastMoveSnapshot(
            room.lastMove.fromRow,
            room.lastMove.fromCol,
            room.lastMove.toRow,
            room.lastMove.toCol,
            room.lastMove.piece,
            room.lastMove.capturedPiece,
            room.lastMove.movedPiece,
            room.lastMove.notation
        );

        return new RoomSnapshot(
            room.roomId,
            room.players.size(),
            PLAYER_LIMIT,
            room.status,
            room.statusMessage,
            room.currentTurnUserId,
            room.currentTurnColor,
            boardCopy,
            new ArrayList<>(room.moveHistory),
            lastMove,
            players
        );
    }

    private String[][] createInitialBoard() {
        return new String[][]{
            {"bR", "bN", "bB", "bQ", "bK", "bB", "bN", "bR"},
            {"bP", "bP", "bP", "bP", "bP", "bP", "bP", "bP"},
            {null, null, null, null, null, null, null, null},
            {null, null, null, null, null, null, null, null},
            {null, null, null, null, null, null, null, null},
            {null, null, null, null, null, null, null, null},
            {"wP", "wP", "wP", "wP", "wP", "wP", "wP", "wP"},
            {"wR", "wN", "wB", "wQ", "wK", "wB", "wN", "wR"}
        };
    }

    private String userIdForColor(RoomState room, String color) {
        return room.players.values().stream()
            .filter(player -> color.equals(player.color))
            .map(player -> player.userId)
            .findFirst()
            .orElse(null);
    }

    private boolean inside(int row, int col) {
        return row >= 0 && col >= 0 && row < BOARD_SIZE && col < BOARD_SIZE;
    }

    private boolean isPawnPromotion(String piece, int targetRow) {
        if (piece == null || piece.length() < 2 || piece.charAt(1) != 'P') {
            return false;
        }
        char side = piece.charAt(0);
        return (side == 'w' && targetRow == 0) || (side == 'b' && targetRow == BOARD_SIZE - 1);
    }

    private String normalizePromotion(String promotion) {
        String value = normalize(promotion);
        if (value == null) {
            return "Q";
        }
        return switch (value.toUpperCase()) {
            case "Q", "R", "B", "N" -> value.toUpperCase();
            default -> "Q";
        };
    }

    private String buildNotation(String originalPiece,
                                 int fromRow,
                                 int fromCol,
                                 int toRow,
                                 int toCol,
                                 String capturedPiece,
                                 String movedPiece,
                                 String promotion) {
        String sideText = (originalPiece != null && originalPiece.startsWith("w")) ? "Trang" : "Den";
        String pieceName = pieceName(originalPiece);
        String action = (capturedPiece == null || capturedPiece.isBlank()) ? "di" : "an";
        String text = sideText + " " + pieceName + " " + action + " " + toSquare(fromRow, fromCol) + " -> " + toSquare(toRow, toCol);
        if (movedPiece != null && originalPiece != null && !movedPiece.equals(originalPiece) && isPawnPromotion(originalPiece, toRow)) {
            text += " (phong " + pieceName(movedPiece) + ")";
        } else if (promotion != null && !promotion.isBlank() && originalPiece != null && originalPiece.endsWith("P") && isPawnPromotion(originalPiece, toRow)) {
            text += " (phong " + pieceName(originalPiece.substring(0, 1) + promotion) + ")";
        }
        return text;
    }

    private String toSquare(int row, int col) {
        char file = (char) ('a' + col);
        int rank = 8 - row;
        return "" + file + rank;
    }

    private String pieceName(String piece) {
        if (piece == null || piece.length() < 2) {
            return "Quan";
        }
        return switch (piece.charAt(1)) {
            case 'K' -> "Vua";
            case 'Q' -> "Hau";
            case 'R' -> "Xe";
            case 'B' -> "Tuong";
            case 'N' -> "Ma";
            case 'P' -> "Tot";
            default -> "Quan";
        };
    }

    private String colorLabel(String color) {
        return "b".equalsIgnoreCase(color) ? "Den" : "Trang";
    }

    private String displayNameOf(PlayerSeatState player) {
        if (player == null) {
            return "Nguoi choi";
        }
        if (player.displayName != null && !player.displayName.isBlank()) {
            return player.displayName;
        }
        return player.userId == null || player.userId.isBlank() ? "Nguoi choi" : player.userId;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    public record JoinResult(boolean ok, String assignedColor, RoomSnapshot room, String error) {
    }

    public record ActionResult(boolean ok, String eventType, RoomSnapshot room, String error) {
        public static ActionResult ok(String eventType, RoomSnapshot room) {
            return new ActionResult(true, eventType, room, null);
        }

        public static ActionResult error(String error) {
            return new ActionResult(false, "ROOM_STATE", null, error);
        }
    }

    public record RoomListRow(String roomId, int playerCount, int playerLimit, String note) {
    }

    public record RoomSnapshot(String roomId,
                               int playerCount,
                               int playerLimit,
                               String status,
                               String statusMessage,
                               String currentTurnUserId,
                               String currentTurnColor,
                               String[][] board,
                               List<String> moveHistory,
                               LastMoveSnapshot lastMove,
                               List<PlayerSnapshot> players) {
    }

    public record PlayerSnapshot(String userId, String displayName, String avatarPath, String color) {
    }

    public record LastMoveSnapshot(int fromRow,
                                   int fromCol,
                                   int toRow,
                                   int toCol,
                                   String piece,
                                   String capturedPiece,
                                   String movedPiece,
                                   String notation) {
    }

    private static final class RoomState {
        private final String roomId;
        private final LinkedHashMap<String, PlayerSeatState> players = new LinkedHashMap<>();
        private String[][] board;
        private String status = STATUS_WAITING;
        private String statusMessage = "Dang cho doi thu vao phong";
        private String currentTurnUserId;
        private String currentTurnColor = "w";
        private final List<String> moveHistory = new ArrayList<>();
        private LastMoveState lastMove;

        private RoomState(String roomId) {
            this.roomId = roomId;
            this.board = new String[][]{
                {"bR", "bN", "bB", "bQ", "bK", "bB", "bN", "bR"},
                {"bP", "bP", "bP", "bP", "bP", "bP", "bP", "bP"},
                {null, null, null, null, null, null, null, null},
                {null, null, null, null, null, null, null, null},
                {null, null, null, null, null, null, null, null},
                {null, null, null, null, null, null, null, null},
                {"wP", "wP", "wP", "wP", "wP", "wP", "wP", "wP"},
                {"wR", "wN", "wB", "wQ", "wK", "wB", "wN", "wR"}
            };
        }
    }

    private static final class PlayerSeatState {
        private final String userId;
        private String displayName;
        private String avatarPath;
        private String color;

        private PlayerSeatState(String userId, String displayName, String avatarPath, String color) {
            this.userId = userId;
            this.displayName = displayName;
            this.avatarPath = avatarPath;
            this.color = color;
        }
    }

    private static final class LastMoveState {
        private final int fromRow;
        private final int fromCol;
        private final int toRow;
        private final int toCol;
        private final String piece;
        private final String capturedPiece;
        private final String movedPiece;
        private final String notation;

        private LastMoveState(int fromRow,
                              int fromCol,
                              int toRow,
                              int toCol,
                              String piece,
                              String capturedPiece,
                              String movedPiece,
                              String notation) {
            this.fromRow = fromRow;
            this.fromCol = fromCol;
            this.toRow = toRow;
            this.toCol = toCol;
            this.piece = piece;
            this.capturedPiece = capturedPiece;
            this.movedPiece = movedPiece;
            this.notation = notation;
        }
    }
}
