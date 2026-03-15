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

        assertEquals("games/monopoly", view);
        assertEquals("", model.getAttribute("defaultRoomId"));
        assertEquals(Boolean.FALSE, model.getAttribute("roomPage"));
        assertEquals(Boolean.TRUE, model.getAttribute("localPage"));
        assertNotNull(model.getAttribute("game"));
        assertNotNull(model.getAttribute("allGames"));
    }

    @Test
    void roomRouteShouldRenderDedicatedRoomMode() {
        MonopolyPageController controller = new MonopolyPageController(new GameCatalogService());
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.roomPage("MONO-1", model);

        assertEquals("games/monopoly", view);
        assertEquals("MONO-1", model.getAttribute("defaultRoomId"));
        assertEquals(Boolean.TRUE, model.getAttribute("roomPage"));
        assertEquals(Boolean.FALSE, model.getAttribute("localPage"));
        assertNotNull(model.getAttribute("game"));
        assertNotNull(model.getAttribute("allGames"));
    }
}
