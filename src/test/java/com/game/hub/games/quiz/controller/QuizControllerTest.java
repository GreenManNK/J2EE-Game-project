package com.game.hub.games.quiz.controller;

import com.game.hub.games.quiz.logic.QuizRoom;
import com.game.hub.games.quiz.service.QuizService;
import com.game.hub.service.GameCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.WebSocketSession;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuizControllerTest {

    @Test
    void quizRoomPagesShouldRenderQuizTemplate() {
        QuizController controller = new QuizController();
        ReflectionTestUtils.setField(controller, "gameCatalogService", new GameCatalogService());
        ConcurrentModel playModel = new ConcurrentModel();
        ConcurrentModel spectateModel = new ConcurrentModel();

        assertEquals("games/quiz", controller.quizRoomPage("QUIZ-ROOM-1", playModel));
        assertEquals("games/quiz", controller.quizSpectatePage("QUIZ-ROOM-1", spectateModel));
        assertEquals("QUIZ-ROOM-1", playModel.getAttribute("defaultRoomId"));
        assertEquals(Boolean.TRUE, playModel.getAttribute("roomPage"));
        assertEquals(Boolean.FALSE, playModel.getAttribute("spectateMode"));
        assertEquals("QUIZ-ROOM-1", spectateModel.getAttribute("defaultRoomId"));
        assertEquals(Boolean.TRUE, spectateModel.getAttribute("roomPage"));
        assertEquals(Boolean.TRUE, spectateModel.getAttribute("spectateMode"));
    }

    @Test
    void quizLocalAndBotPagesShouldRenderPracticeTemplate() {
        QuizController controller = new QuizController();
        ReflectionTestUtils.setField(controller, "gameCatalogService", new GameCatalogService());

        ConcurrentModel localModel = new ConcurrentModel();
        ConcurrentModel botModel = new ConcurrentModel();

        assertEquals("games/quiz-practice", controller.quizLocalPage(localModel));
        assertEquals(Boolean.TRUE, localModel.getAttribute("localPage"));
        assertEquals(Boolean.FALSE, localModel.getAttribute("botPage"));

        assertEquals("games/quiz-practice", controller.quizBotPage("hard", botModel));
        assertEquals(Boolean.FALSE, botModel.getAttribute("localPage"));
        assertEquals(Boolean.TRUE, botModel.getAttribute("botPage"));
        assertEquals("hard", botModel.getAttribute("botDifficulty"));
    }

    @Test
    void getPracticeQuestionsShouldExposeStructuredQuestions() {
        QuizService quizService = new QuizService();
        QuizController controller = new QuizController();
        ReflectionTestUtils.setField(controller, "quizService", quizService);

        Map<String, Object> payload = controller.getPracticeQuestions();

        assertTrue(payload.containsKey("questions"));
        Object questions = payload.get("questions");
        assertTrue(questions instanceof List<?>);
        assertTrue(((List<?>) questions).size() >= 4);
    }

    @Test
    void getAvailableRoomsShouldReturnRoomsWithPlayersOrSpectatorsOnly() {
        QuizService quizService = new QuizService();
        QuizRoom emptyRoom = quizService.createRoom();
        QuizRoom playerRoom = quizService.createRoom();
        QuizRoom spectatorRoom = quizService.createRoom();

        playerRoom.addPlayer(mock(WebSocketSession.class));
        spectatorRoom.addSpectator(mock(WebSocketSession.class));

        QuizController controller = new QuizController();
        ReflectionTestUtils.setField(controller, "quizService", quizService);

        List<Map<String, Object>> rooms = controller.getAvailableRooms();

        assertEquals(2, rooms.size());
        assertTrue(rooms.stream().anyMatch(room -> playerRoom.getRoomId().equals(room.get("id"))));
        assertTrue(rooms.stream().anyMatch(room -> spectatorRoom.getRoomId().equals(room.get("id"))));
        assertTrue(rooms.stream().noneMatch(room -> emptyRoom.getRoomId().equals(room.get("id"))));

        Map<String, Object> playerRoomSummary = rooms.stream()
            .filter(room -> playerRoom.getRoomId().equals(room.get("id")))
            .findFirst()
            .orElseThrow();
        assertEquals(1, playerRoomSummary.get("playerCount"));
        assertEquals(0, playerRoomSummary.get("spectatorCount"));
        assertEquals(0, playerRoomSummary.get("questionNumber"));
        assertEquals(playerRoom.getTotalQuestions(), playerRoomSummary.get("totalQuestions"));
        assertEquals("WAITING", playerRoomSummary.get("gameState"));
    }

    @Test
    void getAvailableRoomsShouldExposeQuestionTimerMetadataForStartedGame() {
        QuizService quizService = new QuizService();
        QuizRoom activeRoom = quizService.createRoom();
        activeRoom.addPlayer(session("quiz-controller-player", "quiz-controller-user"));
        activeRoom.startGame();

        QuizController controller = new QuizController();
        ReflectionTestUtils.setField(controller, "quizService", quizService);

        List<Map<String, Object>> rooms = controller.getAvailableRooms();
        Map<String, Object> roomSummary = rooms.stream()
            .filter(room -> activeRoom.getRoomId().equals(room.get("id")))
            .findFirst()
            .orElseThrow();

        assertEquals("PLAYING", roomSummary.get("gameState"));
        assertEquals(1, roomSummary.get("questionNumber"));
        assertEquals(15, roomSummary.get("questionDurationSeconds"));
        assertNotEquals(0L, roomSummary.get("questionStartedAtEpochMs"));
        assertTrue(((Number) roomSummary.get("questionDeadlineEpochMs")).longValue() > ((Number) roomSummary.get("questionStartedAtEpochMs")).longValue());
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
