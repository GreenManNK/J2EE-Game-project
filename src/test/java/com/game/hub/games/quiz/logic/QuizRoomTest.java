package com.game.hub.games.quiz.logic;

import com.game.hub.games.quiz.service.QuizService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.WebSocketSession;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuizRoomTest {

    @Test
    void startGameShouldOpenQuestionWindow() {
        QuizRoom room = new QuizService().createRoom();
        room.addPlayer(session("quiz-room-player-1", "quiz-room-user-1"));

        boolean started = room.startGame();

        assertTrue(started);
        assertTrue(room.isStarted());
        assertEquals(0, room.getCurrentQuestionIndex());
        assertNotNull(room.getCurrentQuestion());
        assertTrue(room.getQuestionStartedAtEpochMs() > 0);
        assertTrue(room.getQuestionDeadlineEpochMs() > room.getQuestionStartedAtEpochMs());
        assertEquals(15, room.getQuestionDurationSeconds());
    }

    @Test
    void synchronizeQuestionTimerShouldAdvanceQuestionAndFinishGame() {
        QuizRoom room = new QuizService().createRoom();
        room.addPlayer(session("quiz-room-player-2", "quiz-room-user-2"));
        room.startGame();

        expireCurrentQuestion(room);

        boolean advanced = room.synchronizeQuestionTimer();

        assertTrue(advanced);
        assertTrue(room.isStarted());
        assertEquals(1, room.getCurrentQuestionIndex());
        assertTrue(room.getQuestionDeadlineEpochMs() > System.currentTimeMillis());

        for (int i = room.getCurrentQuestionIndex(); i < room.getTotalQuestions(); i++) {
            expireCurrentQuestion(room);
            room.synchronizeQuestionTimer();
        }

        assertFalse(room.isStarted());
        assertTrue(room.isGameOver());
        assertEquals(room.getTotalQuestions(), room.getCurrentQuestionIndex());
        assertEquals(0L, room.getQuestionStartedAtEpochMs());
        assertEquals(0L, room.getQuestionDeadlineEpochMs());
    }

    @Test
    void answerQuestionShouldRejectStaleQuestionNumber() {
        QuizRoom room = new QuizService().createRoom();
        WebSocketSession session = session("quiz-room-player-3", "quiz-room-user-3");
        room.addPlayer(session);
        room.startGame();

        room.nextQuestion();

        assertFalse(room.answerQuestion(session, 1, 1));
        assertTrue(room.answerQuestion(session, 1, 2));
    }

    private void expireCurrentQuestion(QuizRoom room) {
        ReflectionTestUtils.setField(room, "questionStartedAtEpochMs", System.currentTimeMillis() - 20_000L);
        ReflectionTestUtils.setField(room, "questionDeadlineEpochMs", System.currentTimeMillis() - 1_000L);
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
