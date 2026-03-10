package com.game.hub.games.quiz.websocket;

import com.game.hub.service.AchievementService;
import com.game.hub.games.quiz.logic.QuizRoom;
import com.game.hub.games.quiz.repository.HighScoreRepository;
import com.game.hub.games.quiz.service.QuizService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuizSocketTest {

    @Test
    void createStartAndCloseShouldBroadcastQuestionAndRemoveRoomWhenEmpty() throws Exception {
        QuizService quizService = new QuizService();
        AchievementService achievementService = mock(AchievementService.class);

        QuizSocket socket = new QuizSocket();
        ReflectionTestUtils.setField(socket, "quizService", quizService);
        ReflectionTestUtils.setField(socket, "achievementService", achievementService);

        WebSocketSession session = session("quiz-session-1", "quiz-player");

        socket.handleTextMessage(session, new TextMessage("{\"action\":\"create\"}"));
        QuizRoom room = quizService.getAvailableRooms().stream().findFirst().orElseThrow();

        socket.handleTextMessage(session, new TextMessage("{\"action\":\"start\"}"));

        ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeastOnce()).sendMessage(messages.capture());
        List<TextMessage> payloads = messages.getAllValues();
        assertTrue(payloads.stream().anyMatch(message -> message.getPayload().contains("\"question\":\"What is the capital of France?\"")));
        assertTrue(payloads.stream().anyMatch(message -> message.getPayload().contains("\"questionNumber\":1")));
        assertNotNull(quizService.getRoom(room.getRoomId()));

        socket.afterConnectionClosed(session, CloseStatus.NORMAL);
        assertNull(quizService.getRoom(room.getRoomId()));
    }

    @Test
    void joinUnknownRoomShouldSendError() throws Exception {
        QuizService quizService = new QuizService();
        AchievementService achievementService = mock(AchievementService.class);

        QuizSocket socket = new QuizSocket();
        ReflectionTestUtils.setField(socket, "quizService", quizService);
        ReflectionTestUtils.setField(socket, "achievementService", achievementService);

        WebSocketSession session = session("quiz-session-2", "quiz-player-2");

        socket.handleTextMessage(session, new TextMessage("{\"action\":\"join\",\"roomId\":\"missing-room\"}"));

        ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(messages.capture());
        assertTrue(messages.getValue().getPayload().contains("\"error\":\"Room not found\""));
    }

    @Test
    void gameOverShouldAwardQuizAchievementOnlyToTopScorer() throws Exception {
        QuizService quizService = new QuizService();
        ReflectionTestUtils.setField(quizService, "highScoreRepository", mock(HighScoreRepository.class));
        AchievementService achievementService = mock(AchievementService.class);

        QuizSocket socket = new QuizSocket();
        ReflectionTestUtils.setField(socket, "quizService", quizService);
        ReflectionTestUtils.setField(socket, "achievementService", achievementService);

        WebSocketSession firstSession = session("quiz-session-3", "quiz-player-3");
        WebSocketSession secondSession = session("quiz-session-4", "quiz-player-4");

        socket.handleTextMessage(firstSession, new TextMessage("{\"action\":\"create\"}"));
        QuizRoom room = quizService.getAvailableRooms().stream().findFirst().orElseThrow();
        socket.handleTextMessage(secondSession, new TextMessage("{\"action\":\"join\",\"roomId\":\"" + room.getRoomId() + "\"}"));
        socket.handleTextMessage(firstSession, new TextMessage("{\"action\":\"start\"}"));

        socket.handleTextMessage(firstSession, new TextMessage("{\"action\":\"answer\",\"answer\":2}"));
        socket.handleTextMessage(secondSession, new TextMessage("{\"action\":\"answer\",\"answer\":0}"));
        socket.handleTextMessage(firstSession, new TextMessage("{\"action\":\"answer\",\"answer\":[0,2,3]}"));
        socket.handleTextMessage(secondSession, new TextMessage("{\"action\":\"answer\",\"answer\":\"Jupiter\"}"));

        verify(achievementService).checkAndAward("quiz-player-3", "Quiz", true);
        verify(achievementService, never()).checkAndAward("quiz-player-4", "Quiz", true);
    }

    private WebSocketSession session(String sessionId, String principalName) {
        WebSocketSession session = mock(WebSocketSession.class);
        Principal principal = () -> principalName;
        when(session.getId()).thenReturn(sessionId);
        when(session.getPrincipal()).thenReturn(principal);
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}
