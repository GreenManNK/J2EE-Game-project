package com.game.hub.support;

import com.game.hub.entity.GameHistory;
import com.game.hub.service.GameCatalogService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameHistoryPresentationSupportTest {

    @Test
    void describeShouldNormalizeLegacyCaroMatchCode() {
        GameHistory history = new GameHistory();
        history.setId(17L);
        history.setGameCode("Ranked_1743040000000");

        GameHistoryPresentationSupport support = new GameHistoryPresentationSupport(new GameCatalogService());
        GameHistoryPresentationSupport.ViewMetadata metadata = support.describe(history);

        assertEquals("caro", metadata.gameCode());
        assertEquals("Caro", metadata.gameName());
        assertEquals("Ranked_1743040000000", metadata.matchCode());
        assertEquals("Phong xep hang Caro", metadata.locationLabel());
        assertEquals("/games/caro", metadata.locationHref());
        assertEquals("Online", metadata.contextLabel());
    }

    @Test
    void describeShouldPreferStructuredRoomMetadataWhenPresent() {
        GameHistory history = new GameHistory();
        history.setId(23L);
        history.setGameCode("caro");
        history.setMatchCode("Normal_ABC123-1743041234567");
        history.setRoomId("Normal_ABC123");
        history.setLocationLabel("Phong thuong Caro");
        history.setLocationPath("/game/room/Normal_ABC123");

        GameHistoryPresentationSupport support = new GameHistoryPresentationSupport(new GameCatalogService());
        GameHistoryPresentationSupport.ViewMetadata metadata = support.describe(history);

        assertEquals("caro", metadata.gameCode());
        assertEquals("Caro", metadata.gameName());
        assertEquals("Normal_ABC123-1743041234567", metadata.matchCode());
        assertEquals("Phong thuong Caro", metadata.locationLabel());
        assertEquals("/game/room/Normal_ABC123", metadata.locationHref());
        assertEquals("Online", metadata.contextLabel());
    }

    @Test
    void describeShouldNormalizeBotMatchesToBaseGameMetadata() {
        GameHistory history = new GameHistory();
        history.setId(29L);
        history.setGameCode("chess-bot");

        GameHistoryPresentationSupport support = new GameHistoryPresentationSupport(new GameCatalogService());
        GameHistoryPresentationSupport.ViewMetadata metadata = support.describe(history);

        assertEquals("chess", metadata.gameCode());
        assertEquals("Co vua", metadata.gameName());
        assertEquals("TRAN-29", metadata.matchCode());
        assertEquals("Che do bot", metadata.locationLabel());
        assertEquals("/chess/bot", metadata.locationHref());
        assertEquals("/chess/bot", metadata.gameHref());
        assertEquals("Bot", metadata.contextLabel());
    }

    @Test
    void describeShouldNormalizeMonopolyBotMatchesToNativeBotRoute() {
        GameHistory history = new GameHistory();
        history.setId(41L);
        history.setGameCode("monopoly-bot");

        GameHistoryPresentationSupport support = new GameHistoryPresentationSupport(new GameCatalogService());
        GameHistoryPresentationSupport.ViewMetadata metadata = support.describe(history);

        assertEquals("monopoly", metadata.gameCode());
        assertEquals("Co ty phu", metadata.gameName());
        assertEquals("TRAN-41", metadata.matchCode());
        assertEquals("Che do bot", metadata.locationLabel());
        assertEquals("/games/monopoly/bot", metadata.locationHref());
        assertEquals("/games/monopoly/bot", metadata.gameHref());
        assertEquals("Bot", metadata.contextLabel());
    }

    @Test
    void describeShouldNormalizeQuizAndTypingBotMatchesToPracticeRoutes() {
        GameHistoryPresentationSupport support = new GameHistoryPresentationSupport(new GameCatalogService());

        GameHistory quizHistory = new GameHistory();
        quizHistory.setId(51L);
        quizHistory.setGameCode("quiz-bot");
        GameHistoryPresentationSupport.ViewMetadata quizMetadata = support.describe(quizHistory);
        assertEquals("quiz", quizMetadata.gameCode());
        assertEquals("/games/quiz/bot", quizMetadata.locationHref());
        assertEquals("/games/quiz/bot", quizMetadata.gameHref());
        assertEquals("Bot", quizMetadata.contextLabel());

        GameHistory typingHistory = new GameHistory();
        typingHistory.setId(52L);
        typingHistory.setGameCode("typing-bot");
        GameHistoryPresentationSupport.ViewMetadata typingMetadata = support.describe(typingHistory);
        assertEquals("typing", typingMetadata.gameCode());
        assertEquals("/games/typing/bot", typingMetadata.locationHref());
        assertEquals("/games/typing/bot", typingMetadata.gameHref());
        assertEquals("Bot", typingMetadata.contextLabel());
    }
}
