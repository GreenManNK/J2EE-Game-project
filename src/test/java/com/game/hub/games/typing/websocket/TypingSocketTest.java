package com.game.hub.games.typing.websocket;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.hub.games.typing.logic.TypingRoom;
import com.game.hub.games.typing.model.TypingText;
import com.game.hub.games.typing.repository.TypingTextRepository;
import com.game.hub.games.typing.service.TypingService;
import com.game.hub.service.AchievementService;
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

class TypingSocketTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createJoinAndCloseShouldBroadcastCountdownStateAndRemoveRoomWhenEmpty() throws Exception {
        TypingTextRepository textRepository = mock(TypingTextRepository.class);
        when(textRepository.findRandomText()).thenReturn(new TypingText("abc"));
        TypingService typingService = new TypingService(textRepository);
        AchievementService achievementService = mock(AchievementService.class);

        TypingSocket socket = new TypingSocket();
        ReflectionTestUtils.setField(socket, "typingService", typingService);
        ReflectionTestUtils.setField(socket, "achievementService", achievementService);

        WebSocketSession firstSession = session("typing-session-1", "player-1");
        WebSocketSession secondSession = session("typing-session-2", "player-2");

        socket.handleTextMessage(firstSession, new TextMessage("{\"action\":\"create\"}"));

        TypingRoom room = typingService.getAvailableRooms().stream().findFirst().orElseThrow();
        socket.handleTextMessage(secondSession, new TextMessage("{\"action\":\"join\",\"roomId\":\"" + room.getId() + "\"}"));

        ArgumentCaptor<TextMessage> firstMessages = ArgumentCaptor.forClass(TextMessage.class);
        verify(firstSession, atLeastOnce()).sendMessage(firstMessages.capture());
        Map<String, Object> firstPayload = payload(firstMessages.getAllValues().get(firstMessages.getAllValues().size() - 1));
        assertEquals(room.getId(), firstPayload.get("id"));
        assertEquals("COUNTDOWN", String.valueOf(firstPayload.get("gameState")));
        assertEquals(2, ((Number) firstPayload.get("playerCount")).intValue());
        assertEquals("player-1", firstPayload.get("yourId"));
        assertTrue(((Number) firstPayload.get("countdownEndsAtEpochMs")).longValue() > 0);

        ArgumentCaptor<TextMessage> secondMessages = ArgumentCaptor.forClass(TextMessage.class);
        verify(secondSession, atLeastOnce()).sendMessage(secondMessages.capture());
        Map<String, Object> secondPayload = payload(secondMessages.getAllValues().get(secondMessages.getAllValues().size() - 1));
        assertEquals(room.getId(), secondPayload.get("id"));
        assertEquals("COUNTDOWN", String.valueOf(secondPayload.get("gameState")));
        assertEquals(2, ((Number) secondPayload.get("playerCount")).intValue());
        assertEquals("player-2", secondPayload.get("yourId"));

        socket.afterConnectionClosed(firstSession, CloseStatus.NORMAL);
        assertNotNull(typingService.getRoom(room.getId()));

        socket.afterConnectionClosed(secondSession, CloseStatus.NORMAL);
        assertNull(typingService.getRoom(room.getId()));
    }

    @Test
    void joinUnknownRoomShouldSendError() throws Exception {
        TypingTextRepository textRepository = mock(TypingTextRepository.class);
        TypingService typingService = new TypingService(textRepository);
        AchievementService achievementService = mock(AchievementService.class);

        TypingSocket socket = new TypingSocket();
        ReflectionTestUtils.setField(socket, "typingService", typingService);
        ReflectionTestUtils.setField(socket, "achievementService", achievementService);

        WebSocketSession session = session("typing-session-3", "player-3");

        socket.handleTextMessage(session, new TextMessage("{\"action\":\"join\",\"roomId\":\"missing-room\"}"));

        ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(messages.capture());
        Map<String, Object> errorPayload = payload(messages.getValue());
        assertEquals("Room not found", errorPayload.get("error"));
    }

    @Test
    void progressShouldAwardTypingAchievementToWinner() throws Exception {
        TypingTextRepository textRepository = mock(TypingTextRepository.class);
        when(textRepository.findRandomText()).thenReturn(new TypingText("abc"));
        TypingService typingService = new TypingService(textRepository);
        AchievementService achievementService = mock(AchievementService.class);

        TypingSocket socket = new TypingSocket();
        ReflectionTestUtils.setField(socket, "typingService", typingService);
        ReflectionTestUtils.setField(socket, "achievementService", achievementService);

        WebSocketSession firstSession = session("typing-session-4", "player-4");
        WebSocketSession secondSession = session("typing-session-5", "player-5");

        socket.handleTextMessage(firstSession, new TextMessage("{\"action\":\"create\"}"));
        TypingRoom room = typingService.getAvailableRooms().stream().findFirst().orElseThrow();
        socket.handleTextMessage(secondSession, new TextMessage("{\"action\":\"join\",\"roomId\":\"" + room.getId() + "\"}"));
        ReflectionTestUtils.setField(room, "countdownEndsAtEpochMs", System.currentTimeMillis() - 1);

        socket.handleTextMessage(firstSession, new TextMessage("{\"action\":\"progress\",\"roomId\":\"" + room.getId() + "\",\"typed\":\"abc\"}"));

        verify(achievementService).recordRewardedWin("player-4", "Typing");
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
