package com.game.hub.games.typing.controller;

import com.game.hub.games.typing.logic.TypingRoom;
import com.game.hub.games.typing.repository.TypingTextRepository;
import com.game.hub.games.typing.service.TypingService;
import com.game.hub.service.GameCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TypingControllerTest {

    @Test
    void typingRoomPageShouldRenderTypingTemplate() {
        TypingController controller = new TypingController();
        ReflectionTestUtils.setField(controller, "gameCatalogService", new GameCatalogService());
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.typingRoomPage("TYP-ROOM-1", model);

        assertEquals("games/typing", view);
        assertEquals("TYP-ROOM-1", model.getAttribute("defaultRoomId"));
        assertEquals(Boolean.TRUE, model.getAttribute("roomPage"));
    }

    @Test
    void typingPracticeAndBotPagesShouldRenderPracticeTemplate() {
        TypingController controller = new TypingController();
        ReflectionTestUtils.setField(controller, "gameCatalogService", new GameCatalogService());

        ConcurrentModel practiceModel = new ConcurrentModel();
        ConcurrentModel botModel = new ConcurrentModel();

        assertEquals("games/typing-practice", controller.typingPracticePage(practiceModel));
        assertEquals(Boolean.TRUE, practiceModel.getAttribute("practicePage"));
        assertEquals(Boolean.FALSE, practiceModel.getAttribute("botPage"));

        assertEquals("games/typing-practice", controller.typingBotPage("hard", botModel));
        assertEquals(Boolean.TRUE, botModel.getAttribute("botPage"));
        assertEquals("hard", botModel.getAttribute("botDifficulty"));
    }

    @Test
    void getPracticeTextsShouldExposeFallbackPack() {
        TypingTextRepository textRepository = mock(TypingTextRepository.class);
        when(textRepository.findRandomText()).thenReturn(null);

        TypingService typingService = new TypingService(textRepository);
        TypingController controller = new TypingController();
        ReflectionTestUtils.setField(controller, "typingService", typingService);

        Map<String, Object> payload = controller.getPracticeTexts();

        assertTrue(payload.containsKey("texts"));
        Object texts = payload.get("texts");
        assertTrue(texts instanceof List<?>);
        assertTrue(((List<?>) texts).size() >= 4);
    }

    @Test
    void getAvailableRoomsShouldOnlyReturnRoomsWithPlayers() {
        TypingTextRepository textRepository = mock(TypingTextRepository.class);
        when(textRepository.findRandomText()).thenReturn(null);

        TypingService typingService = new TypingService(textRepository);
        TypingRoom emptyRoom = typingService.createRoom();
        TypingRoom activeRoom = typingService.createRoom();
        activeRoom.addPlayer("guest-a");

        TypingController controller = new TypingController();
        ReflectionTestUtils.setField(controller, "typingService", typingService);

        List<Map<String, Object>> rooms = controller.getAvailableRooms();

        assertEquals(1, rooms.size());
        assertEquals(activeRoom.getId(), rooms.get(0).get("id"));
        assertEquals(1, rooms.get(0).get("playerCount"));
        assertEquals(activeRoom.getGameState(), rooms.get(0).get("gameState"));
        assertTrue(((Number) rooms.get(0).get("textLength")).intValue() > 0);
        assertTrue(rooms.stream().noneMatch(room -> emptyRoom.getId().equals(room.get("id"))));
    }
}
