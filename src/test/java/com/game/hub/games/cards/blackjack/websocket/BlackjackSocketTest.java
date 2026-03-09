package com.game.hub.games.cards.blackjack.websocket;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.hub.games.cards.blackjack.logic.BlackjackRoom;
import com.game.hub.games.cards.blackjack.service.BlackjackService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlackjackSocketTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createBetAndCloseShouldBroadcastPlayerTurnAndRemoveRoomWhenEmpty() throws Exception {
        BlackjackService blackjackService = new BlackjackService();

        BlackjackSocket socket = new BlackjackSocket();
        ReflectionTestUtils.setField(socket, "blackjackService", blackjackService);

        WebSocketSession session = session("blackjack-session-1", "blackjack-player");

        socket.handleTextMessage(session, new TextMessage("{\"action\":\"create\"}"));
        BlackjackRoom room = blackjackService.getAvailableRooms().stream().findFirst().orElseThrow();

        socket.handleTextMessage(session, new TextMessage("{\"action\":\"bet\",\"amount\":100}"));

        ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeastOnce()).sendMessage(messages.capture());
        List<TextMessage> payloads = messages.getAllValues();
        Map<String, Object> latestPayload = payload(payloads.get(payloads.size() - 1));
        assertEquals(room.getId(), latestPayload.get("id"));
        assertEquals("PLAYER_TURN", String.valueOf(latestPayload.get("gameState")));
        assertEquals(1, ((Number) latestPayload.get("playerCount")).intValue());
        assertEquals("blackjack-player", latestPayload.get("yourId"));
        assertNotNull(blackjackService.getRoom(room.getId()));

        socket.afterConnectionClosed(session, CloseStatus.NORMAL);
        assertNull(blackjackService.getRoom(room.getId()));
    }

    @Test
    void spectateUnknownRoomShouldSendError() throws Exception {
        BlackjackService blackjackService = new BlackjackService();

        BlackjackSocket socket = new BlackjackSocket();
        ReflectionTestUtils.setField(socket, "blackjackService", blackjackService);

        WebSocketSession session = session("blackjack-session-2", "blackjack-viewer");

        socket.handleTextMessage(session, new TextMessage("{\"action\":\"spectate\",\"roomId\":\"missing-room\"}"));

        ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(messages.capture());
        assertTrue(messages.getValue().getPayload().contains("\"error\":\"Room not found\""));
    }

    private WebSocketSession session(String sessionId, String principalName) {
        WebSocketSession session = mock(WebSocketSession.class);
        Principal principal = () -> principalName;
        when(session.getId()).thenReturn(sessionId);
        when(session.getPrincipal()).thenReturn(principal);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private Map<String, Object> payload(TextMessage message) throws Exception {
        return objectMapper.readValue(message.getPayload(), new TypeReference<>() {});
    }
}
