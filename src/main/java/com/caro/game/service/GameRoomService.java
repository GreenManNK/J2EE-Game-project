package com.caro.game.service;

import com.caro.game.entity.GameHistory;
import com.caro.game.entity.UserAccount;
import com.caro.game.repository.GameHistoryRepository;
import com.caro.game.repository.UserAccountRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
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
        Map<String, String> players = roomPlayers.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>());
        if (players.containsKey(userId)) {
            return new JoinResult(true, players.get(userId), null);
        }
        if (players.size() >= 2) {
            return new JoinResult(false, null, "Room is full");
        }

        String symbol = players.isEmpty() ? "X" : "O";
        players.put(userId, symbol);

        boards.computeIfAbsent(roomId, k -> new String[BOARD_SIZE][BOARD_SIZE]);
        if (!currentTurn.containsKey(roomId)) {
            currentTurn.put(roomId, userId);
        }

        return new JoinResult(true, symbol, null);
    }

    public synchronized MoveResult makeMove(String roomId, String userId, int x, int y) {
        Map<String, String> players = roomPlayers.get(roomId);
        if (players == null || !players.containsKey(userId)) {
            return MoveResult.error("Player does not belong to room");
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

        boolean win = checkWin(board, x, y, symbol);
        if (win) {
            String loserId = players.keySet().stream().filter(id -> !id.equals(userId)).findFirst().orElse(null);
            int moves = countMoves(board);
            Integer winnerScoreBefore = null;
            Integer loserScoreBefore = null;
            String topPlayerIdBeforeMatch = userAccountRepository.findAllByOrderByScoreDesc().stream()
                .findFirst()
                .map(UserAccount::getId)
                .orElse(null);
            if (loserId != null) {
                winnerScoreBefore = userAccountRepository.findById(userId).map(UserAccount::getScore).orElse(null);
                loserScoreBefore = userAccountRepository.findById(loserId).map(UserAccount::getScore).orElse(null);
            }
            persistGameHistory(roomId, players, userId, moves);

            Integer winnerScore = null;
            Integer loserScore = null;
            if (roomId.startsWith("Ranked_") && loserId != null) {
                ScoreUpdate update = updateRankedScore(userId, loserId);
                winnerScore = update.winnerScore();
                loserScore = update.loserScore();
            }

            if (loserId != null) {
                achievementService.evaluateAfterMatch(
                    roomId, userId, loserId, moves, winnerScoreBefore, loserScoreBefore, topPlayerIdBeforeMatch
                );
            }

            return MoveResult.win(symbol, x, y, userId, loserId, winnerScore, loserScore);
        }

        String nextPlayer = players.keySet().stream().filter(id -> !id.equals(userId)).findFirst().orElse(userId);
        currentTurn.put(roomId, nextPlayer);

        return MoveResult.ok(symbol, x, y, nextPlayer);
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

        String next = players.keySet().iterator().next();
        currentTurn.put(roomId, next);
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
        game.setFirstPlayerId(currentTurn.get(roomId));
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

    private boolean checkWin(String[][] board, int x, int y, String symbol) {
        return checkDirection(board, x, y, symbol, 1, 0)
            || checkDirection(board, x, y, symbol, 0, 1)
            || checkDirection(board, x, y, symbol, 1, 1)
            || checkDirection(board, x, y, symbol, 1, -1);
    }

    private boolean checkDirection(String[][] board, int x, int y, String symbol, int dx, int dy) {
        int count = 1;

        for (int i = 1; i < 5; i++) {
            int nx = x + i * dx;
            int ny = y + i * dy;
            if (inside(nx, ny) && symbol.equals(board[nx][ny])) {
                count++;
            } else {
                break;
            }
        }

        for (int i = 1; i < 5; i++) {
            int nx = x - i * dx;
            int ny = y - i * dy;
            if (inside(nx, ny) && symbol.equals(board[nx][ny])) {
                count++;
            } else {
                break;
            }
        }

        return count >= 5;
    }

    private boolean inside(int x, int y) {
        return x >= 0 && x < BOARD_SIZE && y >= 0 && y < BOARD_SIZE;
    }

    public record JoinResult(boolean ok, String symbol, String error) {
    }

    public record MoveResult(boolean ok, boolean win, String symbol, int x, int y, String nextTurnUserId, String winnerUserId,
                             String loserUserId, Integer winnerScore, Integer loserScore, String error) {
        public static MoveResult ok(String symbol, int x, int y, String nextTurnUserId) {
            return new MoveResult(true, false, symbol, x, y, nextTurnUserId, null, null, null, null, null);
        }

        public static MoveResult win(String symbol, int x, int y, String winnerUserId, String loserUserId,
                                     Integer winnerScore, Integer loserScore) {
            return new MoveResult(true, true, symbol, x, y, null, winnerUserId, loserUserId, winnerScore, loserScore, null);
        }

        public static MoveResult error(String error) {
            return new MoveResult(false, false, null, -1, -1, null, null, null, null, null, error);
        }
    }

    private record ScoreUpdate(Integer winnerScore, Integer loserScore) {
    }
}
