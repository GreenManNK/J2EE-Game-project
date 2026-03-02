package com.game.hub.games.typing.logic;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TypingRoom {
    private final String id;
    private String textToType;
    private final Map<String, PlayerProgress> players = new ConcurrentHashMap<>();
    private GameState gameState = GameState.WAITING;
    private String winnerId;

    public TypingRoom(String id, String textToType) {
        this.id = id;
        this.textToType = textToType;
    }

    public boolean addPlayer(String playerId) {
        if (players.size() < 2) {
            players.put(playerId, new PlayerProgress());
            if (players.size() == 2) {
                gameState = GameState.PLAYING;
            }
            return true;
        }
        return false;
    }

    public void removePlayer(String playerId) {
        players.remove(playerId);
        if (playerId != null && playerId.equals(winnerId)) {
            winnerId = null;
        }
        if (gameState != GameState.FINISHED && players.size() < 2) {
            gameState = GameState.WAITING;
        }
    }

    public void updateProgress(String playerId, String typedText) {
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
        gameState = players.size() >= 2 ? GameState.PLAYING : GameState.WAITING;
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
        if (gameState != GameState.FINISHED) {
            return null;
        }
        if (winnerId != null && players.containsKey(winnerId)) {
            return winnerId;
        }
        return players.entrySet().stream()
            .filter(entry -> entry.getValue().isFinished())
            .sorted((left, right) -> {
                long leftTime = left.getValue().getFinishedAtEpochMs();
                long rightTime = right.getValue().getFinishedAtEpochMs();
                if (leftTime == 0 && rightTime == 0) {
                    return Double.compare(right.getValue().getAccuracy(), left.getValue().getAccuracy());
                }
                if (leftTime == 0) {
                    return 1;
                }
                if (rightTime == 0) {
                    return -1;
                }
                int timeCompare = Long.compare(leftTime, rightTime);
                if (timeCompare != 0) {
                    return timeCompare;
                }
                return Double.compare(right.getValue().getAccuracy(), left.getValue().getAccuracy());
            })
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);
    }

    public String getId() { return id; }
    public String getTextToType() { return textToType; }
    public Map<String, PlayerProgress> getPlayers() { return players; }
    public GameState getGameState() { return gameState; }
    public int getPlayerCount() { return players.size(); }

    public enum GameState { WAITING, PLAYING, FINISHED }
}
