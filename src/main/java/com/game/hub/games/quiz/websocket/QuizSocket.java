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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class QuizSocket extends TextWebSocketHandler {

    @Autowired
    private QuizService quizService;

    @Autowired
    private AchievementService achievementService;

    private final Map<WebSocketSession, QuizRoom> sessionToRoomMap = new ConcurrentHashMap<>();
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
            sessionToRoomMap.put(session, room);
            broadcastRoomState(room);
        } else if ("join".equals(action)) {
            String roomId = String.valueOf(payload.getOrDefault("roomId", "")).trim();
            QuizRoom room = quizService.getRoom(roomId);
            if (room != null) {
                room.addPlayer(session);
                sessionToRoomMap.put(session, room);
                broadcastRoomState(room);
            } else {
                sendError(session, "Room not found");
            }
        } else if ("spectate".equals(action)) {
            String roomId = String.valueOf(payload.getOrDefault("roomId", "")).trim();
            QuizRoom room = quizService.getRoom(roomId);
            if (room != null) {
                room.addSpectator(session);
                sessionToRoomMap.put(session, room);
                broadcastRoomState(room);
            } else {
                sendError(session, "Room not found");
            }
        } else if ("start".equals(action)) {
            QuizRoom room = sessionToRoomMap.get(session);
            if (room != null) {
                room.startGame();
                broadcastQuestion(room);
            } else {
                sendError(session, "You are not in a room");
            }
        } else if ("answer".equals(action)) {
            QuizRoom room = sessionToRoomMap.get(session);
            if (room != null) {
                Object answer = payload.get("answer");
                room.answerQuestion(session, answer);
                room.nextQuestion();
                if (room.isGameOver()) {
                    broadcastScores(room);
                } else {
                    broadcastQuestion(room);
                }
            } else {
                sendError(session, "You are not in a room");
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        QuizRoom room = sessionToRoomMap.get(session);
        if (room != null) {
            room.removePlayer(session);
            sessionToRoomMap.remove(session);
            if (room.getPlayers().isEmpty() && room.getSpectators().isEmpty()) {
                quizService.removeRoom(room.getRoomId());
            } else {
                broadcastRoomState(room);
            }
        }
    }

    private void broadcastRoomState(QuizRoom room) throws IOException {
        sendRoomMessage(room, Map.of(
                "roomId", room.getRoomId(),
                "players", room.getPlayers().size(),
                "spectators", room.getSpectators().size(),
                "questionNumber", room.getCurrentQuestionIndex() + 1,
                "totalQuestions", room.getTotalQuestions()
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
        for (Map.Entry<WebSocketSession, Integer> entry : scores.entrySet()) {
            WebSocketSession scoreSession = entry.getKey();
            int score = entry.getValue();
            String playerName = resolvePlayerName(scoreSession);
            scorePayload.put(playerName, score);
            quizService.saveHighScore(playerName, score);

            if (scoreSession.getPrincipal() != null && scoreSession.getPrincipal().getName() != null) {
                String userId = scoreSession.getPrincipal().getName().trim();
                if (!userId.isEmpty()) {
                    achievementService.checkAndAward(userId, "Quiz", score > 0);
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
            room.removePlayer(disconnected);
            sessionToRoomMap.remove(disconnected);
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
}
