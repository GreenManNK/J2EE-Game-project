package com.game.hub.games.typing.logic;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TypingRoom {
    private static final int PLAYER_LIMIT = 4;
    private static final int MIN_PLAYERS_TO_START = 2;
    private static final long COUNTDOWN_MS = 3_000;
    private static final long ROUND_DURATION_MS = 120_000;

    private final String id;
    private String textToType;
    private final Map<String, PlayerProgress> players = new ConcurrentHashMap<>();
    private GameState gameState = GameState.WAITING;
    private String winnerId;
    private long countdownEndsAtEpochMs;
    private long raceStartedAtEpochMs;
    private long raceEndsAtEpochMs;

    public TypingRoom(String id, String textToType) {
        this.id = id;
        this.textToType = textToType;
    }

    public boolean addPlayer(String playerId) {
        if (playerId == null || playerId.isBlank()) {
            return false;
        }
        if (players.containsKey(playerId)) {
            return true;
        }
        if (players.size() >= PLAYER_LIMIT) {
            return false;
        }

        players.put(playerId, new PlayerProgress());
        if (gameState == GameState.WAITING && players.size() >= MIN_PLAYERS_TO_START) {
            beginCountdown();
        }
        return true;
    }

    public void removePlayer(String playerId) {
        PlayerProgress removed = players.remove(playerId);
        if (removed == null) {
            return;
        }
        if (playerId != null && playerId.equals(winnerId)) {
            winnerId = null;
        }
        if ((gameState == GameState.PLAYING || gameState == GameState.COUNTDOWN) && players.size() < MIN_PLAYERS_TO_START) {
            resetToWaiting();
        }
    }

    public void updateProgress(String playerId, String typedText) {
        syncStateWithClock();
        PlayerProgress progress = players.get(playerId);
        if (progress != null && gameState == GameState.PLAYING) {
            String sanitizedTyped = typedText == null ? "" : typedText;
            if (sanitizedTyped.length() > textToType.length()) {
                sanitizedTyped = sanitizedTyped.substring(0, textToType.length());
            }

            progress.setTyped(sanitizedTyped);
            progress.setAccuracy(calculateAccuracy(textToType, sanitizedTyped));

            if (textToType.equals(sanitizedTyped)) {
                gameState = GameState.FINISHED;
                progress.setFinished(true);
                if (progress.getFinishedAtEpochMs() == 0) {
                    progress.setFinishedAtEpochMs(System.currentTimeMillis());
                }
                if (winnerId == null) {
                    winnerId = playerId;
                }
            }
        }
    }

    public void resetRace(String nextText) {
        this.textToType = nextText;
        players.values().forEach(PlayerProgress::resetForRace);
        winnerId = null;
        countdownEndsAtEpochMs = 0;
        raceStartedAtEpochMs = 0;
        raceEndsAtEpochMs = 0;
        if (players.size() >= MIN_PLAYERS_TO_START) {
            beginCountdown();
        } else {
            gameState = GameState.WAITING;
        }
    }

    private double calculateAccuracy(String source, String typed) {
        int correctChars = 0;
        int len = Math.min(source.length(), typed.length());
        for (int i = 0; i < len; i++) {
            if (source.charAt(i) == typed.charAt(i)) {
                correctChars++;
            }
        }
        return len > 0 ? (double) correctChars / typed.length() * 100 : 0;
    }

    public String getWinner() {
        syncStateWithClock();
        if (gameState != GameState.FINISHED) {
            return null;
        }
        if (winnerId != null && players.containsKey(winnerId)) {
            return winnerId;
        }
        return players.entrySet().stream()
            .filter(entry -> entry.getValue() != null)
            .sorted((left, right) -> {
                int finishedCompare = Boolean.compare(right.getValue().isFinished(), left.getValue().isFinished());
                if (finishedCompare != 0) {
                    return finishedCompare;
                }
                long leftTime = left.getValue().getFinishedAtEpochMs();
                long rightTime = right.getValue().getFinishedAtEpochMs();
                if (left.getValue().isFinished() && right.getValue().isFinished()) {
                    int timeCompare = Long.compare(leftTime, rightTime);
                    if (timeCompare != 0) {
                        return timeCompare;
                    }
                }
                int typedCompare = Integer.compare(right.getValue().getTyped().length(), left.getValue().getTyped().length());
                if (typedCompare != 0) {
                    return typedCompare;
                }
                return Double.compare(right.getValue().getAccuracy(), left.getValue().getAccuracy());
            })
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);
    }

    private void beginCountdown() {
        countdownEndsAtEpochMs = System.currentTimeMillis() + COUNTDOWN_MS;
        raceStartedAtEpochMs = 0;
        raceEndsAtEpochMs = 0;
        gameState = GameState.COUNTDOWN;
    }

    private void startRaceNow() {
        raceStartedAtEpochMs = System.currentTimeMillis();
        raceEndsAtEpochMs = raceStartedAtEpochMs + ROUND_DURATION_MS;
        countdownEndsAtEpochMs = 0;
        gameState = GameState.PLAYING;
    }

    private void resetToWaiting() {
        gameState = GameState.WAITING;
        countdownEndsAtEpochMs = 0;
        raceStartedAtEpochMs = 0;
        raceEndsAtEpochMs = 0;
    }

    private void syncStateWithClock() {
        long now = System.currentTimeMillis();
        if (gameState == GameState.COUNTDOWN) {
            if (players.size() < MIN_PLAYERS_TO_START) {
                resetToWaiting();
                return;
            }
            if (countdownEndsAtEpochMs > 0 && now >= countdownEndsAtEpochMs) {
                startRaceNow();
            }
        }
        if (gameState == GameState.PLAYING && raceEndsAtEpochMs > 0 && now >= raceEndsAtEpochMs) {
            gameState = GameState.FINISHED;
            winnerId = getWinner();
        }
    }

    public String getId() { return id; }
    public String getTextToType() { return textToType; }
    public Map<String, PlayerProgress> getPlayers() { return players; }
    public GameState getGameState() {
        syncStateWithClock();
        return gameState;
    }
    public int getPlayerCount() { return players.size(); }
    public int getPlayerLimit() { return PLAYER_LIMIT; }
    public boolean hasPlayer(String playerId) { return playerId != null && players.containsKey(playerId); }
    public long getCountdownEndsAtEpochMs() {
        syncStateWithClock();
        return countdownEndsAtEpochMs;
    }
    public long getRaceStartedAtEpochMs() {
        syncStateWithClock();
        return raceStartedAtEpochMs;
    }
    public long getRaceEndsAtEpochMs() {
        syncStateWithClock();
        return raceEndsAtEpochMs;
    }

    public enum GameState { WAITING, COUNTDOWN, PLAYING, FINISHED }
}
