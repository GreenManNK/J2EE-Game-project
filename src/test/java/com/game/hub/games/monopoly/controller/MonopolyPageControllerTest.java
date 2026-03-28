package com.game.hub.games.monopoly.controller;

import com.game.hub.service.GameCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MonopolyPageControllerTest {

    @Test
    void roomsRouteShouldRedirectToLobby() {
        MonopolyPageController controller = new MonopolyPageController(new GameCatalogService());

        String view = controller.roomsPage();

        assertEquals("redirect:/games/monopoly", view);
    }

    @Test
    void localRouteShouldRenderDedicatedLocalMode() {
        MonopolyPageController controller = new MonopolyPageController(new GameCatalogService());
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.localPage(model);

        assertEquals("games/monopoly-local", view);
        assertEquals("", model.getAttribute("defaultRoomId"));
        assertEquals(Boolean.FALSE, model.getAttribute("roomPage"));
        assertEquals(Boolean.TRUE, model.getAttribute("localPage"));
        assertEquals(Boolean.FALSE, model.getAttribute("botPage"));
        assertNotNull(model.getAttribute("game"));
        assertNotNull(model.getAttribute("allGames"));
    }

    @Test
    void botRouteShouldRenderDedicatedBotMode() {
        MonopolyPageController controller = new MonopolyPageController(new GameCatalogService());
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.botPage("hard", model);

        assertEquals("games/monopoly-bot", view);
        assertEquals("", model.getAttribute("defaultRoomId"));
        assertEquals(Boolean.FALSE, model.getAttribute("roomPage"));
        assertEquals(Boolean.FALSE, model.getAttribute("localPage"));
        assertEquals(Boolean.TRUE, model.getAttribute("botPage"));
        assertEquals(Boolean.FALSE, model.getAttribute("botArenaPage"));
        assertEquals("hard", model.getAttribute("botDifficulty"));
        assertNotNull(model.getAttribute("game"));
        assertNotNull(model.getAttribute("allGames"));
    }

    @Test
    void botArenaRouteShouldRenderDedicatedBotArenaMode() {
        MonopolyPageController controller = new MonopolyPageController(new GameCatalogService());
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.botArenaPage("hard", "Tester", 3, 2000, model);

        assertEquals("games/monopoly-bot-arena", view);
        assertEquals(Boolean.FALSE, model.getAttribute("roomPage"));
        assertEquals(Boolean.FALSE, model.getAttribute("localPage"));
        assertEquals(Boolean.TRUE, model.getAttribute("botPage"));
        assertEquals(Boolean.TRUE, model.getAttribute("botArenaPage"));
        assertEquals("hard", model.getAttribute("botDifficulty"));
        assertEquals("Tester", model.getAttribute("botPlayerName"));
        assertEquals(3, model.getAttribute("botPlayerCount"));
        assertEquals(2000, model.getAttribute("botStartingCash"));
    }

    @Test
    void roomRouteShouldRenderDedicatedRoomMode() {
        MonopolyPageController controller = new MonopolyPageController(new GameCatalogService());
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.roomPage("MONO-1", model);

        assertEquals("games/monopoly-room", view);
        assertEquals("MONO-1", model.getAttribute("defaultRoomId"));
        assertEquals(Boolean.TRUE, model.getAttribute("roomPage"));
        assertEquals(Boolean.FALSE, model.getAttribute("localPage"));
        assertEquals(Boolean.FALSE, model.getAttribute("botPage"));
        assertEquals(Boolean.FALSE, model.getAttribute("botArenaPage"));
        assertNotNull(model.getAttribute("game"));
        assertNotNull(model.getAttribute("allGames"));
    }
}
