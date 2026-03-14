package com.game.hub.controller;

import com.game.hub.service.GameCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GameModeControllerTest {

    @Test
    void botPickerShouldRenderPickerWithDifficultyLinks() {
        GameModeController controller = new GameModeController(new GameCatalogService());
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.botPicker("chess", model);

        assertEquals("game-mode/bot-picker", view);
        assertEquals(Boolean.TRUE, model.getAttribute("hasRealBotNow"));
        assertEquals("/chess/bot?difficulty=easy", model.getAttribute("easyUrl"));
        assertEquals("/chess/bot?difficulty=hard", model.getAttribute("hardUrl"));
        assertEquals("/games/chess/rooms", model.getAttribute("onlinePlayUrl"));
    }

    @Test
    void botPickerShouldRejectUnknownGame() {
        GameModeController controller = new GameModeController(new GameCatalogService());

        assertThrows(ResponseStatusException.class, () -> controller.botPicker("abc", new ConcurrentModel()));
    }

    @Test
    void botPlayPlaceholderShouldRedirectChessToNativeRoute() {
        GameModeController controller = new GameModeController(new GameCatalogService());

        String view = controller.botPlayPlaceholder("chess", "hard", new ConcurrentModel());

        assertEquals("redirect:/chess/bot?difficulty=hard", view);
    }

    @Test
    void botPlayPlaceholderShouldRedirectXiangqiToNativeRoute() {
        GameModeController controller = new GameModeController(new GameCatalogService());

        String view = controller.botPlayPlaceholder("xiangqi", "easy", new ConcurrentModel());

        assertEquals("redirect:/xiangqi/bot?difficulty=easy", view);
    }

    @Test
    void botPlayPlaceholderShouldRedirectCardsToNativeRoute() {
        GameModeController controller = new GameModeController(new GameCatalogService());

        String view = controller.botPlayPlaceholder("cards", "hard", new ConcurrentModel());

        assertEquals("redirect:/cards/tien-len/bot?difficulty=hard", view);
    }

    @Test
    void botPlayPlaceholderShouldRedirectCaroToNativeRoute() {
        GameModeController controller = new GameModeController(new GameCatalogService());

        String view = controller.botPlayPlaceholder("caro", "easy", new ConcurrentModel());

        assertEquals("redirect:/bot/easy", view);
    }
}
