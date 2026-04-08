package com.game.hub.games.typing.logic;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypingRoomTest {

    @Test
    void secondPlayerShouldStartCountdownBeforeRace() {
        TypingRoom room = new TypingRoom("typing-room", "abcdef");

        room.addPlayer("p1");
        room.addPlayer("p2");

        assertEquals(TypingRoom.GameState.COUNTDOWN, room.getGameState());
        assertTrue(room.getCountdownEndsAtEpochMs() > System.currentTimeMillis());
    }

    @Test
    void timeoutShouldFinishRaceAndPickLeaderAsWinner() {
        TypingRoom room = new TypingRoom("typing-room", "abcdefghij");
        room.addPlayer("p1");
        room.addPlayer("p2");

        ReflectionTestUtils.setField(room, "countdownEndsAtEpochMs", System.currentTimeMillis() - 1);
        room.updateProgress("p1", "abcdef");

        @SuppressWarnings("unchecked")
        var players = room.getPlayers();
        PlayerProgress p2 = players.get("p2");
        assertNotNull(p2);
        p2.setTyped("abc");
        p2.setAccuracy(100);

        ReflectionTestUtils.setField(room, "raceEndsAtEpochMs", System.currentTimeMillis() - 1);

        assertEquals(TypingRoom.GameState.FINISHED, room.getGameState());
        assertEquals("p1", room.getWinner());
    }
}
