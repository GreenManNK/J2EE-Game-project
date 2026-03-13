package com.game.hub.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.hub.service.ExternalGameModuleConfig;
import com.game.hub.service.ExternalGameModuleService;
import com.game.hub.service.GameCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.web.server.ResponseStatusException;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

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
        assertEquals("games/minesweeper", controller.detail("minesweeper", new ConcurrentModel()));
        assertEquals("games/cards", controller.detail("cards", new ConcurrentModel()));
        assertEquals("games/cards/blackjack", controller.detail("blackjack", new ConcurrentModel()));
        assertEquals("games/quiz", controller.detail("quiz", new ConcurrentModel()));
        assertEquals("games/typing", controller.detail("typing", new ConcurrentModel()));
        assertEquals("games/puzzle/index", controller.detail("puzzle", new ConcurrentModel()));
        assertEquals("games/monopoly", controller.detail("monopoly", new ConcurrentModel()));
    }

    @Test
    void detailShouldRenderExternalTemplateForImportedModule() throws Exception {
        Path registryFile = Files.createTempFile("external-module-controller", ".json");
        ExternalGameModuleService externalService = new ExternalGameModuleService(
            new ObjectMapper(),
            registryFile,
            Duration.ofSeconds(5),
            Duration.ofSeconds(5),
            HttpClient.newHttpClient()
        );
        externalService.upsertModules(List.of(
            new ExternalGameModuleConfig(
                "go-racer",
                "Go Racer",
                "Go Racer",
                "Game module viet bang Go.",
                "bi-controller",
                true,
                true,
                false,
                true,
                "Mo Go Racer",
                "https://go.example.com/play",
                List.of("Gateway API co san"),
                "external-module",
                "go",
                "redirect",
                "",
                "https://go.example.com/api",
                "https://go.example.com/manifest.json",
                false
            )
        ), false);
        GameCatalogController controller = new GameCatalogController(new GameCatalogService(externalService));

        ConcurrentModel model = new ConcurrentModel();
        String view = controller.detail("go-racer", model);

        assertEquals("games/external-detail", view);
        assertNotNull(model.getAttribute("game"));
    }
}
