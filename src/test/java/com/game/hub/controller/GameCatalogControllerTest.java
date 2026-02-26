package com.game.hub.controller;

import com.game.hub.service.GameCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GameCatalogControllerTest {

    @Test
    void indexShouldRenderCatalogPageWithGames() {
        GameCatalogController controller = new GameCatalogController(new GameCatalogService());
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.index(model);

        assertEquals("games/index", view);
        assertNotNull(model.getAttribute("games"));
    }

    @Test
    void detailShouldReturn404ForUnknownGame() {
        GameCatalogController controller = new GameCatalogController(new GameCatalogService());

        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> controller.detail("unknown-game", new ConcurrentModel())
        );

        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void detailShouldRenderDedicatedViewPerGame() {
        GameCatalogController controller = new GameCatalogController(new GameCatalogService());

        assertEquals("games/caro", controller.detail("caro", new ConcurrentModel()));
        assertEquals("games/chess", controller.detail("chess", new ConcurrentModel()));
        assertEquals("games/xiangqi", controller.detail("xiangqi", new ConcurrentModel()));
        assertEquals("games/cards", controller.detail("cards", new ConcurrentModel()));
    }
}
