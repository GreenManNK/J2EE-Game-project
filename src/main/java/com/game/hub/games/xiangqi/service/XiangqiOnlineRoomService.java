package com.game.hub.games.xiangqi.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class XiangqiOnlineRoomService {
    private static final int ROWS = 10;
    private static final int COLS = 9;
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

        String assignedColor = room.players.values().stream().anyMatch(player -> "r".equals(player.color)) ? "b" : "r";
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
            room.currentTurnColor = "r";
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
            room.currentTurnUserId = userIdForColor(room, "r");
            room.currentTurnColor = "r";
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
        if (!XiangqiMoveRules.isLegalMove(board, fromRow, fromCol, toRow, toCol, player.color)) {
            return ActionResult.error("Illegal move");
        }

        String movedPiece = piece;

        board[fromRow][fromCol] = null;
        board[toRow][toCol] = movedPiece;

        String notation = buildNotation(piece, fromRow, fromCol, toRow, toCol, target);
        room.moveHistory.add(notation);
        room.lastMove = new LastMoveState(fromRow, fromCol, toRow, toCol, piece, target, movedPiece, notation);

        String nextColor = "r".equals(player.color) ? "b" : "r";
        room.currentTurnColor = nextColor;
        room.currentTurnUserId = userIdForColor(room, nextColor);
        room.status = STATUS_PLAYING;

        String winnerColor = player.color;
        if (!XiangqiMoveRules.hasGeneral(board, nextColor)) {
            room.status = STATUS_GAME_OVER;
            room.currentTurnUserId = null;
            room.currentTurnColor = winnerColor;
            room.statusMessage = colorLabel(winnerColor) + " da an Tuong va chien thang!";
            return ActionResult.ok("MOVE", snapshotOf(room));
        }

        boolean opponentInCheck = XiangqiMoveRules.isGeneralInCheck(board, nextColor);
        boolean opponentHasMove = XiangqiMoveRules.hasAnyLegalMove(board, nextColor);
        if (!opponentHasMove && opponentInCheck) {
            room.status = STATUS_GAME_OVER;
            room.currentTurnUserId = null;
            room.currentTurnColor = winnerColor;
            room.statusMessage = "Chieu bi! " + colorLabel(winnerColor) + " thang.";
            return ActionResult.ok("MOVE", snapshotOf(room));
        }
        if (!opponentHasMove) {
            room.status = STATUS_GAME_OVER;
            room.currentTurnUserId = null;
            room.statusMessage = "Het nuoc di hop le - hoa.";
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

        String winnerColor = "r".equals(player.color) ? "b" : "r";
        String winnerUserId = userIdForColor(room, winnerColor);
        PlayerSeatState winner = winnerUserId == null ? null : room.players.get(winnerUserId);
        if (winner == null) {
            return ActionResult.error("Opponent not found");
        }

        String loserName = displayNameOf(player);
        String winnerName = displayNameOf(winner);
        String loserColorLabel = "r".equals(player.color) ? "Do" : "Den";

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
            room.currentTurnColor = "r";
            room.currentTurnUserId = userIdForColor(room, "r");
            room.statusMessage = statusMessage == null || statusMessage.isBlank()
                ? "Da san sang choi"
                : statusMessage;
        } else {
            room.status = STATUS_WAITING;
            room.currentTurnColor = "r";
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
            player.color = (i == 0) ? "r" : "b";
            room.players.put(player.userId, player);
        }
    }

    private RoomSnapshot snapshotOf(RoomState room) {
        String[][] boardCopy = new String[ROWS][COLS];
        for (int i = 0; i < ROWS; i++) {
            boardCopy[i] = Arrays.copyOf(room.board[i], COLS);
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

    private static String[][] createInitialBoard() {
        String[][] board = new String[ROWS][COLS];
        board[0] = new String[]{"bR", "bH", "bE", "bA", "bG", "bA", "bE", "bH", "bR"};
        board[2][1] = "bC";
        board[2][7] = "bC";
        board[3][0] = "bP";
        board[3][2] = "bP";
        board[3][4] = "bP";
        board[3][6] = "bP";
        board[3][8] = "bP";

        board[9] = new String[]{"rR", "rH", "rE", "rA", "rG", "rA", "rE", "rH", "rR"};
        board[7][1] = "rC";
        board[7][7] = "rC";
        board[6][0] = "rP";
        board[6][2] = "rP";
        board[6][4] = "rP";
        board[6][6] = "rP";
        board[6][8] = "rP";
        return board;
    }

    private String userIdForColor(RoomState room, String color) {
        return room.players.values().stream()
            .filter(player -> color.equals(player.color))
            .map(player -> player.userId)
            .findFirst()
            .orElse(null);
    }

    private boolean inside(int row, int col) {
        return row >= 0 && col >= 0 && row < ROWS && col < COLS;
    }

    private String buildNotation(String originalPiece,
                                 int fromRow,
                                 int fromCol,
                                 int toRow,
                                 int toCol,
                                 String capturedPiece) {
        String sideText = (originalPiece != null && originalPiece.startsWith("r")) ? "Do" : "Den";
        String pieceName = pieceName(originalPiece);
        String action = (capturedPiece == null || capturedPiece.isBlank()) ? "di" : "an";
        return sideText + " " + pieceName + " " + action + " " + toSquare(fromRow, fromCol) + " -> " + toSquare(toRow, toCol);
    }

    private String toSquare(int row, int col) {
        return (col + 1) + "-" + (ROWS - row);
    }

    private String pieceName(String piece) {
        if (piece == null || piece.length() < 2) {
            return "Quan";
        }
        return switch (piece.charAt(1)) {
            case 'G' -> "Tuong";
            case 'A' -> "Si";
            case 'E' -> "Tinh";
            case 'H' -> "Ma";
            case 'R' -> "Xe";
            case 'C' -> "Phao";
            case 'P' -> "Tot";
            default -> "Quan";
        };
    }

    private String colorLabel(String color) {
        return "b".equalsIgnoreCase(color) ? "Den" : "Do";
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
        private String currentTurnColor = "r";
        private final List<String> moveHistory = new ArrayList<>();
        private LastMoveState lastMove;

        private RoomState(String roomId) {
            this.roomId = roomId;
            this.board = createInitialBoard();
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

