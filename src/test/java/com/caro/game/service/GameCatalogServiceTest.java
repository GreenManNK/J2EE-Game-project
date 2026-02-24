package com.caro.game.service;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameCatalogServiceTest {

    @Test
    void shouldExposeCaroAndThreeAdditionalGames() {
        GameCatalogService service = new GameCatalogService();

        var games = service.findAll();
        Set<String> codes = games.stream().map(GameCatalogItem::code).collect(Collectors.toSet());

        assertEquals(4, games.size());
        assertTrue(codes.contains("caro"));
        assertTrue(codes.contains("chess"));
        assertTrue(codes.contains("xiangqi"));
        assertTrue(codes.contains("cards"));
        assertTrue(service.findByCode("CARO").isPresent());
        var chess = service.findByCode("chess").orElseThrow();
        assertTrue(chess.availableNow());
        assertTrue(chess.supportsOffline());
        assertTrue(chess.supportsOnline());
        assertNotNull(chess.primaryActionUrl());

        var cards = service.findByCode("cards").orElseThrow();
        assertTrue(cards.availableNow());
        assertTrue(cards.supportsOnline());
        assertTrue(cards.supportsOffline());
        assertEquals("/cards/tien-len", cards.primaryActionUrl());

        var xiangqi = service.findByCode("xiangqi").orElseThrow();
        assertTrue(xiangqi.availableNow());
        assertTrue(xiangqi.supportsOffline());
        assertTrue(xiangqi.supportsOnline());
        assertEquals("/online-hub?game=xiangqi", xiangqi.primaryActionUrl());
    }
}
