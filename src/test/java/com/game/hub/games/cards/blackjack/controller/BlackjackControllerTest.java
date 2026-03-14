package com.game.hub.games.cards.blackjack.controller;

import com.game.hub.games.cards.blackjack.logic.BlackjackRoom;
import com.game.hub.games.cards.blackjack.service.BlackjackService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlackjackControllerTest {

    @Test
    void blackjackPageShouldRenderDedicatedRoomLobbyWhenRoomMissing() {
        BlackjackController controller = new BlackjackController();

        assertEquals("games/cards/blackjack", controller.blackjackPage(null, null));
    }

    @Test
    void blackjackRoomPagesShouldRenderBlackjackTemplate() {
        BlackjackController controller = new BlackjackController();

        assertEquals("games/cards/blackjack", controller.blackjackRoomPage("BJ-ROOM-1"));
        assertEquals("games/cards/blackjack", controller.blackjackSpectatePage("BJ-ROOM-1"));
    }

    @Test
    void getAvailableRoomsShouldReturnRoomsWithPlayersOrSpectatorsOnly() {
        BlackjackService blackjackService = new BlackjackService();
        BlackjackRoom emptyRoom = blackjackService.createRoom();
        BlackjackRoom playerRoom = blackjackService.createRoom();
        BlackjackRoom spectatorRoom = blackjackService.createRoom();

        playerRoom.addPlayer("guest-a");
        spectatorRoom.addSpectator("viewer-a");

        BlackjackController controller = new BlackjackController();
        ReflectionTestUtils.setField(controller, "blackjackService", blackjackService);

        List<Map<String, Object>> rooms = controller.getAvailableRooms();

        assertEquals(2, rooms.size());
        assertTrue(rooms.stream().anyMatch(room -> playerRoom.getId().equals(room.get("id"))));
        assertTrue(rooms.stream().anyMatch(room -> spectatorRoom.getId().equals(room.get("id"))));
        assertTrue(rooms.stream().noneMatch(room -> emptyRoom.getId().equals(room.get("id"))));

        Map<String, Object> playerRoomSummary = rooms.stream()
            .filter(room -> playerRoom.getId().equals(room.get("id")))
            .findFirst()
            .orElseThrow();
        assertEquals(1, playerRoomSummary.get("playerCount"));
        assertEquals(0, playerRoomSummary.get("spectatorCount"));
        assertEquals(playerRoom.getGameState(), playerRoomSummary.get("gameState"));
    }
}
