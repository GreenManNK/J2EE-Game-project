package com.game.hub.games.typing.websocket;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.hub.games.typing.logic.TypingRoom;
import com.game.hub.games.typing.service.TypingService;
import com.game.hub.service.AchievementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TypingSocket extends TextWebSocketHandler {
    private final Map<WebSocketSession, TypingRoom> sessionToRoomMap = new ConcurrentHashMap<>();
    private final Map<String, Set<WebSocketSession>> roomToSessionsMap = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, String> sessionPlayerIds = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private TypingService typingService;

    @Autowired
    private AchievementService achievementService;

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> payload = objectMapper.readValue(message.getPayload(), new TypeReference<Map<String, Object>>() {});
        String action = String.valueOf(payload.getOrDefault("action", "")).trim();
        if (action.isEmpty()) {
            return;
        }

        if ("create".equals(action)) {
            TypingRoom room = typingService.createRoom();
            String playerId = resolvePlayerId(session);
            room.addPlayer(playerId);
            bindSessionToRoom(session, room, playerId);
            broadcastRoom(room);
            return;
        }

        if ("join".equals(action)) {
            String roomId = String.valueOf(payload.getOrDefault("roomId", "")).trim();
            if (roomId.isEmpty()) {
                sendError(session, "Room ID is required");
                return;
            }
            TypingRoom room = typingService.getRoom(roomId);
            if (room == null) {
                sendError(session, "Room not found");
                return;
            }
            String playerId = resolvePlayerId(session);
            if (!room.hasPlayer(playerId) && !room.addPlayer(playerId)) {
                sendError(session, "Room is full");
                return;
            }
            bindSessionToRoom(session, room, playerId);
            broadcastRoom(room);
            return;
        }

        if ("leave".equals(action)) {
            unbindSession(session, true);
            return;
        }

        if ("progress".equals(action)) {
            TypingRoom room = resolveRoomForSession(session, payload);
            if (room == null) {
                sendError(session, "Room not found for current session");
                return;
            }
            String typedText = String.valueOf(payload.getOrDefault("typed", ""));
            String playerId = resolvePlayerId(session);
            if (!room.hasPlayer(playerId)) {
                sendError(session, "You are not a player in this room");
                return;
            }
            room.updateProgress(playerId, typedText);
            broadcastRoom(room);
            if (room.getGameState() == TypingRoom.GameState.FINISHED) {
                String winnerId = room.getWinner();
                if (winnerId != null && room.grantWinRewardOnce()) {
                    achievementService.recordRewardedWin(winnerId, "Typing");
                }
            }
            return;
        }

        if ("rematch".equals(action)) {
            TypingRoom room = resolveRoomForSession(session, payload);
            if (room == null) {
                sendError(session, "Room not found for rematch");
                return;
            }
            String playerId = resolvePlayerId(session);
            if (!room.hasPlayer(playerId)) {
                sendError(session, "Only players in room can rematch");
                return;
            }
            TypingRoom resetRoom = typingService.resetRoomRace(room.getId());
            if (resetRoom == null) {
                sendError(session, "Unable to reset room");
                return;
            }
            broadcastRoom(resetRoom);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        unbindSession(session, true);
    }

    private void bindSessionToRoom(WebSocketSession session, TypingRoom room, String playerId) throws Exception {
        TypingRoom previousRoom = sessionToRoomMap.get(session);
        if (previousRoom != null && !previousRoom.getId().equals(room.getId())) {
            unbindSession(session, true);
        }
        sessionToRoomMap.put(session, room);
        sessionPlayerIds.put(session, playerId);
        roomToSessionsMap.computeIfAbsent(room.getId(), key -> ConcurrentHashMap.newKeySet()).add(session);
    }

    private TypingRoom resolveRoomForSession(WebSocketSession session, Map<String, Object> payload) {
        TypingRoom room = sessionToRoomMap.get(session);
        if (room != null) {
            return room;
        }
        String roomId = String.valueOf(payload.getOrDefault("roomId", "")).trim();
        if (roomId.isEmpty()) {
            return null;
        }
        return typingService.getRoom(roomId);
    }

    private void broadcastRoom(TypingRoom room) throws Exception {
        if (room == null) {
            return;
        }
        Set<WebSocketSession> sessions = roomToSessionsMap.get(room.getId());
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        List<WebSocketSession> disconnectedSessions = new ArrayList<>();
        for (WebSocketSession ws : new ArrayList<>(sessions)) {
            if (ws.isOpen()) {
                Map<String, Object> roomPayload = new LinkedHashMap<>();
                roomPayload.put("id", room.getId());
                roomPayload.put("textToType", room.getTextToType());
                roomPayload.put("players", room.getPlayers());
                roomPayload.put("gameState", room.getGameState());
                roomPayload.put("winner", room.getWinner());
                roomPayload.put("countdownEndsAtEpochMs", room.getCountdownEndsAtEpochMs());
                roomPayload.put("raceStartedAtEpochMs", room.getRaceStartedAtEpochMs());
                roomPayload.put("raceEndsAtEpochMs", room.getRaceEndsAtEpochMs());
                roomPayload.put("yourId", sessionPlayerIds.get(ws));
                roomPayload.put("playerCount", room.getPlayers().size());
                roomPayload.put("playerLimit", room.getPlayerLimit());
                String payload = objectMapper.writeValueAsString(roomPayload);
                try {
                    ws.sendMessage(new TextMessage(payload));
                } catch (Exception ex) {
                    disconnectedSessions.add(ws);
                }
            } else {
                disconnectedSessions.add(ws);
            }
        }
        for (WebSocketSession disconnected : disconnectedSessions) {
            pruneSession(disconnected);
        }
    }

    private void sendError(WebSocketSession session, String message) throws Exception {
        if (session != null && session.isOpen()) {
            String payload = objectMapper.writeValueAsString(Map.of("error", message));
            session.sendMessage(new TextMessage(payload));
        }
    }

    private String resolvePlayerId(WebSocketSession session) {
        String existing = sessionPlayerIds.get(session);
        if (existing != null && !existing.isBlank()) {
            return existing;
        }
        if (session != null && session.getPrincipal() != null && session.getPrincipal().getName() != null) {
            String principalName = session.getPrincipal().getName().trim();
            if (!principalName.isEmpty()) {
                return principalName;
            }
        }
        String sessionId = session == null ? "" : String.valueOf(session.getId());
        if (sessionId.length() > 8) {
            return "guest-" + sessionId.substring(sessionId.length() - 8);
        }
        return "guest-" + sessionId;
    }

    private void pruneSession(WebSocketSession session) {
        try {
            unbindSession(session, false);
        } catch (Exception ignored) {
        }
    }

    private void unbindSession(WebSocketSession session, boolean broadcastAfter) throws Exception {
        TypingRoom room = sessionToRoomMap.remove(session);
        String playerId = sessionPlayerIds.remove(session);
        if (room == null) {
            return;
        }

        Set<WebSocketSession> sessions = roomToSessionsMap.get(room.getId());
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                roomToSessionsMap.remove(room.getId());
                typingService.removeRoom(room.getId());
                return;
            }
        }

        if (playerId != null && !playerId.isBlank() && !hasOtherSessionForPlayer(room.getId(), playerId, session)) {
            room.removePlayer(playerId);
        }

        if (broadcastAfter && typingService.getRoom(room.getId()) != null) {
            broadcastRoom(room);
        }
    }

    private boolean hasOtherSessionForPlayer(String roomId, String playerId, WebSocketSession excludingSession) {
        if (roomId == null || roomId.isBlank() || playerId == null || playerId.isBlank()) {
            return false;
        }
        Set<WebSocketSession> sessions = roomToSessionsMap.get(roomId);
        if (sessions == null || sessions.isEmpty()) {
            return false;
        }
        for (WebSocketSession session : sessions) {
            if (session == null || session.equals(excludingSession)) {
                continue;
            }
            String boundPlayerId = sessionPlayerIds.get(session);
            if (playerId.equals(boundPlayerId)) {
                return true;
            }
        }
        return false;
    }
}
