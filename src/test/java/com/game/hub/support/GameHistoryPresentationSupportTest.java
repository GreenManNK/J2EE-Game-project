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
}
