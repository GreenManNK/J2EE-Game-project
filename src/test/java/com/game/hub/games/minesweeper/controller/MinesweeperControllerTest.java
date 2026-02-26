package com.game.hub.games.minesweeper.controller;

import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinesweeperControllerTest {

    @Test
    void indexShouldRenderMinesweeperTemplateWithNormalizedLevel() {
        MinesweeperController controller = new MinesweeperController();
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.index("expert", model);

        assertEquals("minesweeper/index", view);
        assertEquals("expert", model.getAttribute("initialLevel"));
    }

    @Test
    void indexShouldFallbackToBeginnerForUnknownLevel() {
        MinesweeperController controller = new MinesweeperController();
        ConcurrentModel model = new ConcurrentModel();

        controller.index("custom", model);

        assertEquals("beginner", model.getAttribute("initialLevel"));
    }
}
