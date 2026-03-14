package com.game.hub.games.quiz.websocket;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.hub.service.AchievementService;
import com.game.hub.games.quiz.logic.QuizRoom;
import com.game.hub.games.quiz.service.QuizService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class QuizSocket extends TextWebSocketHandler {

    @Autowired
    private QuizService quizService;

    @Autowired
    private AchievementService achievementService;

    private final Map<WebSocketSession, QuizRoom> sessionToRoomMap = new ConcurrentHashMap<>();
    private final Map<String, Set<WebSocketSession>> roomToSessionsMap = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // The user will join a room via a message, so we don't do anything here.
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> payload = objectMapper.readValue(message.getPayload(), new TypeReference<Map<String, Object>>() {});
        String action = String.valueOf(payload.getOrDefault("action", "")).trim();
        if (action.isEmpty()) {
            return;
        }

        if ("create".equals(action)) {
            QuizRoom room = quizService.createRoom();
            room.addPlayer(session);
            bindSessionToRoom(session, room);
            broadcastRoomState(room);
            return;
        }

        if ("join".equals(action)) {
            String roomId = String.valueOf(payload.getOrDefault("roomId", "")).trim();
            QuizRoom room = quizService.getRoom(roomId);
            if (room != null) {
                room.addPlayer(session);
                bindSessionToRoom(session, room);
                broadcastRoomState(room);
            } else {
                sendError(session, "Room not found");
            }
            return;
        }

        if ("spectate".equals(action)) {
            String roomId = String.valueOf(payload.getOrDefault("roomId", "")).trim();
            QuizRoom room = quizService.getRoom(roomId);
            if (room != null) {
                if (!room.addSpectator(session)) {
                    sendError(session, "Spectator limit reached");
                    return;
                }
                bindSessionToRoom(session, room);
                broadcastRoomState(room);
            } else {
                sendError(session, "Room not found");
            }
            return;
        }

        if ("leave".equals(action)) {
            unbindSession(session, true);
            return;
        }

        if ("start".equals(action)) {
            QuizRoom room = resolveRoomForSession(session, payload);
            if (room == null) {
                sendError(session, "You are not in a room");
                return;
            }
            String playerId = resolvePlayerId(session);
            String hostPlayerId = room.getHostPlayerId();
            if (hostPlayerId != null && !hostPlayerId.isBlank() && !hostPlayerId.equals(playerId)) {
                sendError(session, "Only room host can start game");
                return;
            }
            if (!room.startGame()) {
                sendError(session, "Room has no players to start");
                return;
            }
            broadcastQuestion(room);
            return;
        }

        if ("answer".equals(action)) {
            QuizRoom room = resolveRoomForSession(session, payload);
            if (room != null) {
                Object answer = payload.get("answer");
                boolean accepted = room.answerQuestion(session, answer);
                if (!accepted) {
                    sendError(session, "Answer not accepted");
                    return;
                }
                if (!room.hasEveryoneAnswered()) {
                    broadcastRoomState(room);
                    return;
                }
                room.nextQuestion();
                if (room.isGameOver()) {
                    broadcastScores(room);
                } else {
                    broadcastQuestion(room);
                }
            } else {
                sendError(session, "You are not in a room");
            }
            return;
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        unbindSession(session, true);
    }

    private void broadcastRoomState(QuizRoom room) throws IOException {
        if (room == null) {
            return;
        }
        int totalQuestions = room.getTotalQuestions();
        int questionNumber;
        if (room.isGameOver()) {
            questionNumber = totalQuestions;
        } else if (room.isStarted()) {
            questionNumber = Math.min(room.getCurrentQuestionIndex() + 1, Math.max(totalQuestions, 1));
        } else {
            questionNumber = 0;
        }
        String gameState = room.isGameOver() ? "FINISHED" : (room.isStarted() ? "PLAYING" : "WAITING");

        sendRoomMessage(room, Map.of(
            "roomId", room.getRoomId(),
            "players", room.getPlayers().size(),
            "spectators", room.getSpectators().size(),
            "hostPlayerId", room.getHostPlayerId() == null ? "" : room.getHostPlayerId(),
            "answeredCount", room.getAnsweredCount(),
            "questionNumber", questionNumber,
            "totalQuestions", totalQuestions,
            "gameState", gameState
        ));
    }

    private void broadcastQuestion(QuizRoom room) throws IOException {
        if (room.getCurrentQuestion() == null) {
            return;
        }
        Map<String, Object> questionPayload = objectMapper.convertValue(
                room.getCurrentQuestion(),
                new TypeReference<Map<String, Object>>() {}
        );
        questionPayload.put("questionNumber", room.getCurrentQuestionIndex() + 1);
        questionPayload.put("totalQuestions", room.getTotalQuestions());
        sendRoomMessage(room, questionPayload);
    }

    private void broadcastScores(QuizRoom room) throws IOException {
        Map<WebSocketSession, Integer> scores = room.getScores();
        Map<String, Integer> scorePayload = new LinkedHashMap<>();
        int topScore = scores.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<Map.Entry<WebSocketSession, Integer>> orderedScores = scores.entrySet().stream()
            .sorted(Map.Entry.<WebSocketSession, Integer>comparingByValue(Comparator.reverseOrder()))
            .toList();
        for (Map.Entry<WebSocketSession, Integer> entry : orderedScores) {
            WebSocketSession scoreSession = entry.getKey();
            int score = entry.getValue();
            String playerName = resolvePlayerName(scoreSession);
            scorePayload.put(playerName, score);
            quizService.saveHighScore(playerName, score);

            if (scoreSession.getPrincipal() != null && scoreSession.getPrincipal().getName() != null) {
                String userId = scoreSession.getPrincipal().getName().trim();
                if (!userId.isEmpty() && score > 0 && score == topScore) {
                    achievementService.checkAndAward(userId, "Quiz", true);
                }
            }
        }

        sendRoomMessage(room, scorePayload);
    }

    private void sendRoomMessage(QuizRoom room, Map<String, ?> payload) throws IOException {
        String message = objectMapper.writeValueAsString(payload);
        List<WebSocketSession> disconnectedSessions = new ArrayList<>();
        for (WebSocketSession player : new ArrayList<>(room.getPlayers().keySet())) {
            if (player.isOpen()) {
                try {
                    player.sendMessage(new TextMessage(message));
                } catch (Exception ex) {
                    disconnectedSessions.add(player);
                }
            } else {
                disconnectedSessions.add(player);
            }
        }
        for (WebSocketSession spectator : new ArrayList<>(room.getSpectators())) {
            if (spectator.isOpen()) {
                try {
                    spectator.sendMessage(new TextMessage(message));
                } catch (Exception ex) {
                    disconnectedSessions.add(spectator);
                }
            } else {
                disconnectedSessions.add(spectator);
            }
        }
        for (WebSocketSession disconnected : disconnectedSessions) {
            unbindSessionQuietly(disconnected);
        }
        if (room.getPlayers().isEmpty() && room.getSpectators().isEmpty()) {
            quizService.removeRoom(room.getRoomId());
        }
    }

    private void sendError(WebSocketSession session, String message) throws IOException {
        if (session != null && session.isOpen()) {
            String payload = objectMapper.writeValueAsString(Map.of("error", message));
            session.sendMessage(new TextMessage(payload));
        }
    }

    private String resolvePlayerName(WebSocketSession session) {
        if (session != null && session.getPrincipal() != null && session.getPrincipal().getName() != null) {
            String name = session.getPrincipal().getName().trim();
            if (!name.isEmpty()) {
                return name;
            }
        }
        String sessionId = session == null ? "" : String.valueOf(session.getId());
        if (sessionId.length() > 8) {
            return "guest-" + sessionId.substring(sessionId.length() - 8);
        }
        return "guest-" + sessionId;
    }

    private String resolvePlayerId(WebSocketSession session) {
        if (session != null && session.getPrincipal() != null && session.getPrincipal().getName() != null) {
            String principalName = session.getPrincipal().getName().trim();
            if (!principalName.isEmpty()) {
                return principalName;
            }
        }
        return session == null ? "" : String.valueOf(session.getId());
    }

    private QuizRoom resolveRoomForSession(WebSocketSession session, Map<String, Object> payload) {
        QuizRoom room = sessionToRoomMap.get(session);
        if (room != null) {
            return room;
        }
        String roomId = String.valueOf(payload.getOrDefault("roomId", "")).trim();
        if (roomId.isEmpty()) {
            return null;
        }
        return quizService.getRoom(roomId);
    }

    private void bindSessionToRoom(WebSocketSession session, QuizRoom room) throws IOException {
        QuizRoom previous = sessionToRoomMap.get(session);
        if (previous != null && !previous.getRoomId().equals(room.getRoomId())) {
            unbindSession(session, true);
        }
        sessionToRoomMap.put(session, room);
        roomToSessionsMap.computeIfAbsent(room.getRoomId(), key -> ConcurrentHashMap.newKeySet()).add(session);
    }

    private void unbindSession(WebSocketSession session, boolean broadcastAfter) throws IOException {
        QuizRoom room = sessionToRoomMap.remove(session);
        if (room == null) {
            return;
        }
        room.removePlayer(session);

        Set<WebSocketSession> roomSessions = roomToSessionsMap.get(room.getRoomId());
        if (roomSessions != null) {
            roomSessions.remove(session);
            if (roomSessions.isEmpty()) {
                roomToSessionsMap.remove(room.getRoomId());
            }
        }

        if (room.getPlayers().isEmpty() && room.getSpectators().isEmpty()) {
            quizService.removeRoom(room.getRoomId());
            return;
        }

        if (room.isStarted() && room.hasEveryoneAnswered()) {
            room.nextQuestion();
            if (room.isGameOver()) {
                broadcastScores(room);
            } else {
                broadcastQuestion(room);
            }
            return;
        }

        if (broadcastAfter) {
            broadcastRoomState(room);
        }
    }

    private void unbindSessionQuietly(WebSocketSession session) {
        try {
            unbindSession(session, false);
        } catch (Exception ignored) {
        }
    }
}
