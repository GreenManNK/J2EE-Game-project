package com.game.hub.games.xiangqi.controller;

import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XiangqiControllerTest {

    @Test
    void offlineShouldRenderXiangqiOfflineTemplate() {
        XiangqiController controller = new XiangqiController();
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.offline(model);

        assertEquals("xiangqi/offline", view);
        assertEquals(false, model.getAttribute("botEnabled"));
        assertEquals("easy", model.getAttribute("botDifficulty"));
    }

    @Test
    void botShouldRenderXiangqiOfflineTemplateWithEasyDifficulty() {
        XiangqiController controller = new XiangqiController();
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.bot("easy", model);

        assertEquals("xiangqi/offline", view);
        assertEquals(true, model.getAttribute("botEnabled"));
        assertEquals("easy", model.getAttribute("botDifficulty"));
        assertTrue(String.valueOf(model.getAttribute("pageHeading")).contains("BOT"));
    }

    @Test
    void botShouldNormalizeDifficultyToHardOrEasy() {
        XiangqiController controller = new XiangqiController();
        ConcurrentModel hardModel = new ConcurrentModel();
        ConcurrentModel fallbackModel = new ConcurrentModel();

        controller.bot("hard", hardModel);
        controller.bot("impossible", fallbackModel);

        assertEquals("hard", hardModel.getAttribute("botDifficulty"));
        assertEquals("easy", fallbackModel.getAttribute("botDifficulty"));
    }
}
