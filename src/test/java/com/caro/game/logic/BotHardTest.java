package com.caro.game.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BotHardTest {

    @Test
    void botHardShouldReturnValidMove() {
        BotHard.resetBoard();
        BotHard.Move move = BotHard.getNextMove(7, 7);
        assertTrue(move.x() >= 0 && move.x() < 15);
        assertTrue(move.y() >= 0 && move.y() < 15);
    }
}