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
    void botPickerShouldExposeMonopolyBotAsNativeMode() {
        GameModeController controller = new GameModeController(new GameCatalogService());
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.botPicker("monopoly", model);

        assertEquals("game-mode/bot-picker", view);
        assertEquals(Boolean.TRUE, model.getAttribute("hasRealBotNow"));
        assertEquals("/games/monopoly/bot?difficulty=easy", model.getAttribute("easyUrl"));
        assertEquals("/games/monopoly/bot?difficulty=hard", model.getAttribute("hardUrl"));
    }

    @Test
    void botPickerShouldExposeQuizAndTypingNativeBotRoutes() {
        GameModeController controller = new GameModeController(new GameCatalogService());

        ConcurrentModel quizModel = new ConcurrentModel();
        String quizView = controller.botPicker("quiz", quizModel);
        assertEquals("game-mode/bot-picker", quizView);
        assertEquals(Boolean.TRUE, quizModel.getAttribute("hasRealBotNow"));
        assertEquals("/games/quiz/bot?difficulty=easy", quizModel.getAttribute("easyUrl"));
        assertEquals("/games/quiz/bot?difficulty=hard", quizModel.getAttribute("hardUrl"));

        ConcurrentModel typingModel = new ConcurrentModel();
        String typingView = controller.botPicker("typing", typingModel);
        assertEquals("game-mode/bot-picker", typingView);
        assertEquals(Boolean.TRUE, typingModel.getAttribute("hasRealBotNow"));
        assertEquals("/games/typing/bot?difficulty=easy", typingModel.getAttribute("easyUrl"));
        assertEquals("/games/typing/bot?difficulty=hard", typingModel.getAttribute("hardUrl"));
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

    @Test
    void botPlayPlaceholderShouldRedirectMonopolyToNativeRoute() {
        GameModeController controller = new GameModeController(new GameCatalogService());

        String view = controller.botPlayPlaceholder("monopoly", "hard", new ConcurrentModel());

        assertEquals("redirect:/games/monopoly/bot?difficulty=hard", view);
    }

    @Test
    void botPlayPlaceholderShouldRedirectQuizAndTypingToNativeRoutes() {
        GameModeController controller = new GameModeController(new GameCatalogService());

        assertEquals("redirect:/games/quiz/bot?difficulty=hard", controller.botPlayPlaceholder("quiz", "hard", new ConcurrentModel()));
        assertEquals("redirect:/games/typing/bot?difficulty=easy", controller.botPlayPlaceholder("typing", "easy", new ConcurrentModel()));
    }
}
