package com.game.hub.caro.service;

import com.game.hub.entity.GameHistory;
import com.game.hub.entity.UserAccount;
import com.game.hub.repository.GameHistoryRepository;
import com.game.hub.repository.UserAccountRepository;
import com.game.hub.service.AchievementService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameRoomService {
    private static final int BOARD_SIZE = 10;

    private final Map<String, Map<String, String>> roomPlayers = new ConcurrentHashMap<>();
    private final Map<String, String[][]> boards = new ConcurrentHashMap<>();
    private final Map<String, String> currentTurn = new ConcurrentHashMap<>();

    private final UserAccountRepository userAccountRepository;
    private final GameHistoryRepository gameHistoryRepository;
    private final AchievementService achievementService;

    public GameRoomService(UserAccountRepository userAccountRepository,
                           GameHistoryRepository gameHistoryRepository,
                           AchievementService achievementService) {
        this.userAccountRepository = userAccountRepository;
        this.gameHistoryRepository = gameHistoryRepository;
        this.achievementService = achievementService;
    }

    public synchronized JoinResult joinRoom(String roomId, String userId) {
        if (roomId == null || roomId.isBlank() || userId == null || userId.isBlank()) {
            return new JoinResult(false, null, "Invalid room or user", null, 0);
        }

        Map<String, String> players = roomPlayers.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>());
        if (players.containsKey(userId)) {
            return new JoinResult(true, players.get(userId), null, currentTurn.get(roomId), players.size());
        }
        if (players.size() >= 2) {
            return new JoinResult(false, null, "Room is full", currentTurn.get(roomId), players.size());
        }

        String symbol = nextAvailableSymbol(players);
        if (symbol == null) {
            return new JoinResult(false, null, "Room is full", currentTurn.get(roomId), players.size());
        }
        players.put(userId, symbol);

        boards.computeIfAbsent(roomId, k -> new String[BOARD_SIZE][BOARD_SIZE]);
        if (!currentTurn.containsKey(roomId)) {
            currentTurn.put(roomId, userId);
        }

        return new JoinResult(true, symbol, null, currentTurn.get(roomId), players.size());
    }

    public synchronized MoveResult makeMove(String roomId, String userId, int x, int y) {
        Map<String, String> players = roomPlayers.get(roomId);
        if (players == null || !players.containsKey(userId)) {
            return MoveResult.error("Player does not belong to room");
        }

        if (players.size() < 2) {
            return MoveResult.error("Waiting for opponent");
        }

        if (!userId.equals(currentTurn.get(roomId))) {
            return MoveResult.error("Not your turn");
        }

        if (x < 0 || x >= BOARD_SIZE || y < 0 || y >= BOARD_SIZE) {
            return MoveResult.error("Invalid position");
        }

        String[][] board = boards.get(roomId);
        if (board[x][y] != null) {
            return MoveResult.error("Cell already occupied");
        }

        String symbol = players.get(userId);
        board[x][y] = symbol;
        int totalMoves = countMoves(board);

        List<BoardPoint> winLine = findWinLine(board, x, y, symbol);
        boolean win = winLine != null;
        if (win) {
            String loserId = players.keySet().stream().filter(id -> !id.equals(userId)).findFirst().orElse(null);
            Integer winnerScoreBefore = null;
            Integer loserScoreBefore = null;
            String topPlayerIdBeforeMatch = topPlayerIdByScore();
            if (loserId != null) {
                winnerScoreBefore = userAccountRepository.findById(userId).map(UserAccount::getScore).orElse(null);
                loserScoreBefore = userAccountRepository.findById(loserId).map(UserAccount::getScore).orElse(null);
            }
            persistGameHistory(roomId, players, userId, totalMoves);

            Integer winnerScore = null;
            Integer loserScore = null;
            if (roomId.startsWith("Ranked_") && loserId != null) {
                ScoreUpdate update = updateRankedScore(userId, loserId);
                winnerScore = update.winnerScore();
                loserScore = update.loserScore();
            }

            if (loserId != null) {
                achievementService.evaluateAfterMatch(
                    roomId, userId, loserId, totalMoves, winnerScoreBefore, loserScoreBefore, topPlayerIdBeforeMatch
                );
            }

            currentTurn.remove(roomId);
            return MoveResult.win(symbol, x, y, userId, loserId, winnerScore, loserScore, winLine);
        }

        if (totalMoves >= BOARD_SIZE * BOARD_SIZE) {
            currentTurn.remove(roomId);
            return MoveResult.draw(symbol, x, y);
        }

        String nextPlayer = players.keySet().stream().filter(id -> !id.equals(userId)).findFirst().orElse(userId);
        currentTurn.put(roomId, nextPlayer);

        return MoveResult.ok(symbol, x, y, nextPlayer);
    }

    public synchronized SurrenderResult surrender(String roomId, String userId) {
        Map<String, String> players = roomPlayers.get(roomId);
        if (players == null || !players.containsKey(userId)) {
            return SurrenderResult.error("Player does not belong to room");
        }
        if (players.size() < 2) {
            return SurrenderResult.error("Waiting for opponent");
        }

        String winnerId = players.keySet().stream().filter(id -> !id.equals(userId)).findFirst().orElse(null);
        if (winnerId == null || winnerId.isBlank()) {
            return SurrenderResult.error("Opponent not found");
        }

        String[][] board = boards.get(roomId);
        int moves = board == null ? 0 : countMoves(board);
        Integer winnerScoreBefore = userAccountRepository.findById(winnerId).map(UserAccount::getScore).orElse(null);
        Integer loserScoreBefore = userAccountRepository.findById(userId).map(UserAccount::getScore).orElse(null);
        String topPlayerIdBeforeMatch = topPlayerIdByScore();

        persistGameHistory(roomId, players, winnerId, moves);

        Integer winnerScore = null;
        Integer loserScore = null;
        if (roomId.startsWith("Ranked_")) {
            ScoreUpdate update = updateRankedScore(winnerId, userId);
            winnerScore = update.winnerScore();
            loserScore = update.loserScore();
        }

        achievementService.evaluateAfterMatch(
            roomId, winnerId, userId, moves, winnerScoreBefore, loserScoreBefore, topPlayerIdBeforeMatch
        );

        currentTurn.remove(roomId);
        return SurrenderResult.ok(winnerId, userId, winnerScore, loserScore);
    }

    public synchronized void leaveRoom(String roomId, String userId) {
        Map<String, String> players = roomPlayers.get(roomId);
        if (players == null) {
            return;
        }

        players.remove(userId);
        if (players.isEmpty()) {
            roomPlayers.remove(roomId);
            boards.remove(roomId);
            currentTurn.remove(roomId);
            return;
        }

        // Keep the room waiting for a fresh round when the next player joins.
        boards.put(roomId, new String[BOARD_SIZE][BOARD_SIZE]);
        currentTurn.remove(roomId);
    }

    public synchronized List<String> availableRooms() {
        List<String> result = new ArrayList<>();
        roomPlayers.forEach((roomId, players) -> {
            if (players.size() == 1) {
                result.add(roomId);
            }
        });
        return result;
    }

    public synchronized void resetRoom(String roomId) {
        if (!boards.containsKey(roomId)) {
            return;
        }
        boards.put(roomId, new String[BOARD_SIZE][BOARD_SIZE]);
        Map<String, String> players = roomPlayers.get(roomId);
        if (players != null && !players.isEmpty()) {
            String xPlayer = players.entrySet().stream()
                .filter(e -> "X".equals(e.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(players.keySet().iterator().next());
            currentTurn.put(roomId, xPlayer);
        }
    }

    public synchronized String[][] getBoardSnapshot(String roomId) {
        String[][] board = boards.get(roomId);
        String[][] copy = new String[BOARD_SIZE][BOARD_SIZE];
        if (board == null) {
            return copy;
        }
        for (int i = 0; i < BOARD_SIZE; i++) {
            copy[i] = Arrays.copyOf(board[i], BOARD_SIZE);
        }
        return copy;
    }

    public synchronized String getCurrentTurnUserId(String roomId) {
        return currentTurn.get(roomId);
    }

    private void persistGameHistory(String roomId, Map<String, String> players, String winnerId, int moves) {
        List<Map.Entry<String, String>> entries = players.entrySet().stream().toList();
        if (entries.size() < 2) {
            return;
        }

        String playerX = entries.stream().filter(e -> "X".equals(e.getValue())).map(Map.Entry::getKey).findFirst().orElse(entries.get(0).getKey());
        String playerO = entries.stream().filter(e -> "O".equals(e.getValue())).map(Map.Entry::getKey).findFirst().orElse(entries.get(1).getKey());

        String prefix = "game_";
        if (roomId.startsWith("Ranked_")) prefix = "Ranked_";
        else if (roomId.startsWith("Normal_")) prefix = "Normal_";
        else if (roomId.startsWith("Challenge_")) prefix = "Challenge_";

        GameHistory game = new GameHistory();
        game.setGameCode(prefix + Instant.now().toEpochMilli());
        game.setPlayer1Id(playerX);
        game.setPlayer2Id(playerO);
        game.setFirstPlayerId(playerX);
        game.setTotalMoves(moves);
        game.setWinnerId(winnerId);
        gameHistoryRepository.save(game);
    }

    private ScoreUpdate updateRankedScore(String winnerId, String loserId) {
        UserAccount winner = userAccountRepository.findById(winnerId).orElse(null);
        UserAccount loser = userAccountRepository.findById(loserId).orElse(null);

        if (winner == null || loser == null) {
            return new ScoreUpdate(null, null);
        }

        if (winner.getScore() < loser.getScore() / 2) {
            winner.setScore(winner.getScore() + 10);
            loser.setScore(Math.max(0, loser.getScore() - 15));
        } else {
            winner.setScore(winner.getScore() + 5);
            loser.setScore(Math.max(0, loser.getScore() - 5));
        }

        if (winner.getScore() > winner.getHighestScore()) {
            winner.setHighestScore(winner.getScore());
        }
        if (loser.getScore() > loser.getHighestScore()) {
            loser.setHighestScore(loser.getScore());
        }

        userAccountRepository.save(winner);
        userAccountRepository.save(loser);
        return new ScoreUpdate(winner.getScore(), loser.getScore());
    }

    private int countMoves(String[][] board) {
        int moves = 0;
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                if (board[i][j] != null) moves++;
            }
        }
        return moves;
    }

    private String topPlayerIdByScore() {
        return userAccountRepository.findTopByOrderByScoreDesc()
            .map(UserAccount::getId)
            .orElse(null);
    }

    private List<BoardPoint> findWinLine(String[][] board, int x, int y, String symbol) {
        List<BoardPoint> line = collectDirectionLine(board, x, y, symbol, 1, 0);
        if (line != null) return line;
        line = collectDirectionLine(board, x, y, symbol, 0, 1);
        if (line != null) return line;
        line = collectDirectionLine(board, x, y, symbol, 1, 1);
        if (line != null) return line;
        return collectDirectionLine(board, x, y, symbol, 1, -1);
    }

    private List<BoardPoint> collectDirectionLine(String[][] board, int x, int y, String symbol, int dx, int dy) {
        if (symbol == null || !inside(x, y) || !symbol.equals(board[x][y])) {
            return null;
        }
        List<BoardPoint> line = new ArrayList<>();
        line.add(new BoardPoint(x, y));

        for (int i = 1; i < 5; i++) {
            int nx = x + i * dx;
            int ny = y + i * dy;
            if (inside(nx, ny) && symbol.equals(board[nx][ny])) {
                line.add(new BoardPoint(nx, ny));
            } else {
                break;
            }
        }

        for (int i = 1; i < 5; i++) {
            int nx = x - i * dx;
            int ny = y - i * dy;
            if (inside(nx, ny) && symbol.equals(board[nx][ny])) {
                line.add(0, new BoardPoint(nx, ny));
            } else {
                break;
            }
        }

        return line.size() >= 5 ? line : null;
    }

    private boolean inside(int x, int y) {
        return x >= 0 && x < BOARD_SIZE && y >= 0 && y < BOARD_SIZE;
    }

    private String nextAvailableSymbol(Map<String, String> players) {
        if (players == null || players.isEmpty()) {
            return "X";
        }
        boolean hasX = players.values().stream().anyMatch("X"::equals);
        boolean hasO = players.values().stream().anyMatch("O"::equals);
        if (!hasX) return "X";
        if (!hasO) return "O";
        return null;
    }

    public record JoinResult(boolean ok, String symbol, String error, String currentTurnUserId, int playerCount) {
    }

    public record BoardPoint(int x, int y) {
    }

    public record MoveResult(boolean ok, boolean win, boolean draw, String symbol, int x, int y, String nextTurnUserId,
                             String winnerUserId, String loserUserId, Integer winnerScore, Integer loserScore,
                             List<BoardPoint> winLine, String error) {
        public static MoveResult ok(String symbol, int x, int y, String nextTurnUserId) {
            return new MoveResult(true, false, false, symbol, x, y, nextTurnUserId, null, null, null, null, null, null);
        }

        public static MoveResult win(String symbol, int x, int y, String winnerUserId, String loserId,
                                     Integer winnerScore, Integer loserScore, List<BoardPoint> winLine) {
            return new MoveResult(true, true, false, symbol, x, y, null, winnerUserId, loserId, winnerScore, loserScore, winLine, null);
        }

        public static MoveResult draw(String symbol, int x, int y) {
            return new MoveResult(true, false, true, symbol, x, y, null, null, null, null, null, null, null);
        }

        public static MoveResult error(String error) {
            return new MoveResult(false, false, false, null, -1, -1, null, null, null, null, null, null, error);
        }
    }

    public record SurrenderResult(boolean ok, String winnerUserId, String loserUserId,
                                  Integer winnerScore, Integer loserScore, String error) {
        public static SurrenderResult ok(String winnerUserId, String loserUserId,
                                         Integer winnerScore, Integer loserScore) {
            return new SurrenderResult(true, winnerUserId, loserUserId, winnerScore, loserScore, null);
        }

        public static SurrenderResult error(String error) {
            return new SurrenderResult(false, null, null, null, null, error);
        }
    }

    private record ScoreUpdate(Integer winnerScore, Integer loserScore) {
    }
}

