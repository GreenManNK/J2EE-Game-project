package com.game.hub.games.quiz.controller;

import com.game.hub.games.quiz.logic.QuizRoom;
import com.game.hub.games.quiz.service.QuizService;
import com.game.hub.service.GameCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class QuizControllerTest {

    @Test
    void quizRoomPagesShouldRenderQuizTemplate() {
        QuizController controller = new QuizController();
        ReflectionTestUtils.setField(controller, "gameCatalogService", new GameCatalogService());

        assertEquals("games/quiz", controller.quizRoomPage("QUIZ-ROOM-1", new ConcurrentModel()));
        assertEquals("games/quiz", controller.quizSpectatePage("QUIZ-ROOM-1", new ConcurrentModel()));
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
}
