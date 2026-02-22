package com.caro.game.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BotEasyTest {

    @Test
    void botEasyShouldReturnValidMove() {
        BotEasy.resetBoard();
        BotEasy.placePlayerMove(7, 7);
        BotEasy.Move move = BotEasy.getNextMove(7, 7);
        assertTrue(move.x() >= 0 && move.x() < 15);
        assertTrue(move.y() >= 0 && move.y() < 15);
    }
}