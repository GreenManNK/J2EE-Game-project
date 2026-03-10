package com.game.hub.games.cards.blackjack.websocket;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.hub.games.cards.blackjack.logic.BlackjackRoom;
import com.game.hub.games.cards.blackjack.model.BlackjackPlayer;
import com.game.hub.games.cards.blackjack.service.BlackjackService;
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
public class BlackjackSocket extends TextWebSocketHandler {
    private final Map<WebSocketSession, BlackjackRoom> sessionToRoomMap = new ConcurrentHashMap<>();
    private final Map<String, Set<WebSocketSession>> roomToSessionsMap = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, String> sessionPlayerIds = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private BlackjackService blackjackService;

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
            BlackjackRoom room = blackjackService.createRoom();
            String playerId = resolvePlayerId(session);
            room.addPlayer(playerId);
            bindSessionToRoom(session, room, playerId);
            broadcastRoom(room);
            return;
        }

        if ("join".equals(action)) {
            String roomId = String.valueOf(payload.getOrDefault("roomId", "")).trim();
            BlackjackRoom room = blackjackService.getRoom(roomId);
            if (room == null) {
                sendError(session, "Room not found");
                return;
            }
            String playerId = resolvePlayerId(session);
            room.addPlayer(playerId);
            bindSessionToRoom(session, room, playerId);
            broadcastRoom(room);
            return;
        }

        if ("spectate".equals(action)) {
            String roomId = String.valueOf(payload.getOrDefault("roomId", "")).trim();
            BlackjackRoom room = blackjackService.getRoom(roomId);
            if (room == null) {
                sendError(session, "Room not found");
                return;
            }
            String spectatorId = resolvePlayerId(session);
            room.addSpectator(spectatorId);
            bindSessionToRoom(session, room, spectatorId);
            broadcastRoom(room);
            return;
        }

        BlackjackRoom room = sessionToRoomMap.get(session);
        if (room == null) {
            String roomId = String.valueOf(payload.getOrDefault("roomId", "")).trim();
            if (!roomId.isEmpty()) {
                room = blackjackService.getRoom(roomId);
            }
        }
        if (room == null) {
            sendError(session, "Room not found for current session");
            return;
        }

        String playerId = resolvePlayerId(session);
        if ("bet".equals(action)) {
            BlackjackPlayer player = room.getPlayers().get(playerId);
            if (player == null) {
                sendError(session, "You are not a player in this room");
                return;
            }
            int amount = parseInt(payload.get("amount"));
            if (amount <= 0) {
                sendError(session, "Bet amount must be greater than 0");
                return;
            }
            try {
                player.placeBet(amount);
                if (room.getGameState() == BlackjackRoom.GameState.WAITING && room.hasAnyBetPlaced()) {
                    room.startRound();
                }
            } catch (IllegalArgumentException ex) {
                sendError(session, ex.getMessage());
                return;
            }
            broadcastRoom(room);
            awardRoundWinners(room);
            return;
        }

        if ("hit".equals(action)) {
            if (!room.canPlayerAct(playerId)) {
                sendError(session, "You cannot hit right now");
                return;
            }
            room.playerHit(playerId);
            broadcastRoom(room);
            awardRoundWinners(room);
            return;
        }

        if ("double".equals(action)) {
            BlackjackPlayer player = room.getPlayers().get(playerId);
            if (player == null) {
                sendError(session, "You are not a player in this room");
                return;
            }
            if (!room.canPlayerDouble(playerId)) {
                sendError(session, "Double is only allowed on first two cards with enough balance");
                return;
            }
            try {
                player.placeAdditionalBet(player.getCurrentBet());
            } catch (IllegalArgumentException ex) {
                sendError(session, ex.getMessage());
                return;
            }
            room.playerHit(playerId);
            room.playerStand(playerId);
            broadcastRoom(room);
            awardRoundWinners(room);
            return;
        }

        if ("stand".equals(action)) {
            if (!room.canPlayerAct(playerId)) {
                sendError(session, "You cannot stand right now");
                return;
            }
            room.playerStand(playerId);
            broadcastRoom(room);
            awardRoundWinners(room);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        BlackjackRoom room = sessionToRoomMap.remove(session);
        String playerId = sessionPlayerIds.remove(session);
        if (room == null) {
            return;
        }
        if (playerId != null && !playerId.isBlank()) {
            room.removePlayer(playerId);
        }
        Set<WebSocketSession> sessions = roomToSessionsMap.get(room.getId());
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                roomToSessionsMap.remove(room.getId());
                blackjackService.removeRoom(room.getId());
            }
        }
        if (blackjackService.getRoom(room.getId()) != null) {
            broadcastRoom(room);
        }
    }

    private void bindSessionToRoom(WebSocketSession session, BlackjackRoom room, String playerId) {
        sessionToRoomMap.put(session, room);
        sessionPlayerIds.put(session, playerId);
        roomToSessionsMap.computeIfAbsent(room.getId(), key -> ConcurrentHashMap.newKeySet()).add(session);
    }

    private void broadcastRoom(BlackjackRoom room) throws Exception {
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
                Map<String, Object> payloadMap = new LinkedHashMap<>();
                payloadMap.put("id", room.getId());
                payloadMap.put("players", room.getPlayers());
                payloadMap.put("spectators", room.getSpectators());
                payloadMap.put("dealer", room.getDealer());
                payloadMap.put("gameState", room.getGameState());
                payloadMap.put("yourId", sessionPlayerIds.get(ws));
                payloadMap.put("playerCount", room.getPlayers().size());
                String payload = objectMapper.writeValueAsString(payloadMap);
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
            pruneSession(room, disconnected);
        }
    }

    private void sendError(WebSocketSession session, String message) throws Exception {
        if (session != null && session.isOpen()) {
            String payload = objectMapper.writeValueAsString(Map.of("error", message));
            session.sendMessage(new TextMessage(payload));
        }
    }

    private int parseInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ex) {
            return 0;
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

    private void pruneSession(BlackjackRoom room, WebSocketSession session) {
        sessionToRoomMap.remove(session);
        String playerId = sessionPlayerIds.remove(session);
        if (playerId != null && !playerId.isBlank()) {
            room.removePlayer(playerId);
        }
        Set<WebSocketSession> sessions = roomToSessionsMap.get(room.getId());
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                roomToSessionsMap.remove(room.getId());
                blackjackService.removeRoom(room.getId());
            }
        }
    }

    private void awardRoundWinners(BlackjackRoom room) {
        if (room == null || room.getGameState() != BlackjackRoom.GameState.WAITING) {
            return;
        }
        List<String> winnerIds = room.getWinningPlayerIds();
        if (winnerIds == null || winnerIds.isEmpty()) {
            return;
        }
        for (String winnerId : winnerIds) {
            achievementService.checkAndAward(winnerId, "Blackjack", true);
        }
        room.clearRoundOutcomes();
    }
}
