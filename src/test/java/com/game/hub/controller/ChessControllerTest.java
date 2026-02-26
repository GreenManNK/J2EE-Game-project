package com.game.hub.controller;

import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChessControllerTest {

    @Test
    void offlineShouldRenderChessOfflineTemplate() {
        ChessController controller = new ChessController();
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.offline(model);

        assertEquals("chess/offline", view);
        assertEquals(false, model.getAttribute("botEnabled"));
    }

    @Test
    void botShouldRenderChessOfflineTemplateWithBotMode() {
        ChessController controller = new ChessController();
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.bot("easy", model);

        assertEquals("chess/offline", view);
        assertEquals(true, model.getAttribute("botEnabled"));
        assertTrue(String.valueOf(model.getAttribute("pageHeading")).contains("BOT"));
        assertEquals("easy", model.getAttribute("botDifficulty"));
    }

    @Test
    void botShouldSupportHardDifficulty() {
        ChessController controller = new ChessController();
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.bot("hard", model);

        assertEquals("chess/offline", view);
        assertEquals("hard", model.getAttribute("botDifficulty"));
        assertTrue(String.valueOf(model.getAttribute("pageHeading")).contains("HARD"));
    }
}
