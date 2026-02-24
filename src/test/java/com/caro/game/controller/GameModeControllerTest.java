package com.caro.game.controller;

import com.caro.game.service.GameCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GameModeControllerTest {

    @Test
    void botPickerShouldRedirectToDedicatedGamePage() {
        GameModeController controller = new GameModeController(new GameCatalogService());
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.botPicker("chess", model);

        assertEquals("redirect:/games/chess", view);
        assertEquals(0, model.size());
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
}
