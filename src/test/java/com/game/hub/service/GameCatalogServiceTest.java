package com.game.hub.service;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameCatalogServiceTest {

    @Test
    void shouldExposeAllGamesAddedToCatalog() {
        GameCatalogService service = new GameCatalogService();

        var games = service.findAll();
        Set<String> codes = games.stream().map(GameCatalogItem::code).collect(Collectors.toSet());

        assertEquals(9, games.size());
        assertTrue(codes.contains("caro"));
        assertTrue(codes.contains("chess"));
        assertTrue(codes.contains("xiangqi"));
        assertTrue(codes.contains("minesweeper"));
        assertTrue(codes.contains("cards"));
        assertTrue(codes.contains("blackjack"));
        assertTrue(codes.contains("quiz"));
        assertTrue(codes.contains("typing"));
        assertTrue(codes.contains("puzzle"));
        assertTrue(service.findByCode("CARO").isPresent());
        var caro = service.findByCode("caro").orElseThrow();
        assertTrue(caro.availableNow());
        assertTrue(caro.supportsOnline());
        assertTrue(caro.supportsOffline());
        assertEquals("/games/caro", caro.primaryActionUrl());

        var chess = service.findByCode("chess").orElseThrow();
        assertTrue(chess.availableNow());
        assertTrue(chess.supportsOffline());
        assertTrue(chess.supportsOnline());
        assertNotNull(chess.primaryActionUrl());
        assertEquals("/games/chess", chess.primaryActionUrl());

        var cards = service.findByCode("cards").orElseThrow();
        assertTrue(cards.availableNow());
        assertTrue(cards.supportsOnline());
        assertTrue(cards.supportsOffline());
        assertEquals("/games/cards", cards.primaryActionUrl());

        var blackjack = service.findByCode("blackjack").orElseThrow();
        assertTrue(blackjack.availableNow());
        assertTrue(blackjack.supportsOnline());
        assertEquals(false, blackjack.supportsOffline());
        assertEquals("/games/cards/blackjack", blackjack.primaryActionUrl());

        var xiangqi = service.findByCode("xiangqi").orElseThrow();
        assertTrue(xiangqi.availableNow());
        assertTrue(xiangqi.supportsOffline());
        assertTrue(xiangqi.supportsOnline());
        assertEquals("/games/xiangqi", xiangqi.primaryActionUrl());

        var minesweeper = service.findByCode("minesweeper").orElseThrow();
        assertTrue(minesweeper.availableNow());
        assertTrue(minesweeper.supportsOffline());
        assertTrue(minesweeper.supportsGuest());
        assertEquals(false, minesweeper.supportsOnline());
        assertEquals("/games/minesweeper", minesweeper.primaryActionUrl());

        var quiz = service.findByCode("quiz").orElseThrow();
        assertTrue(quiz.availableNow());
        assertTrue(quiz.supportsOnline());
        assertEquals("/games/quiz", quiz.primaryActionUrl());

        var typing = service.findByCode("typing").orElseThrow();
        assertTrue(typing.availableNow());
        assertTrue(typing.supportsOnline());
        assertEquals("/games/typing", typing.primaryActionUrl());

        var puzzle = service.findByCode("puzzle").orElseThrow();
        assertTrue(puzzle.availableNow());
        assertTrue(puzzle.supportsOffline());
        assertEquals(false, puzzle.supportsOnline());
        assertEquals("/games/puzzle", puzzle.primaryActionUrl());
    }
}
