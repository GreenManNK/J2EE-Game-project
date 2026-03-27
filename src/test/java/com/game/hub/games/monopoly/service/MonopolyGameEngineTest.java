package com.game.hub.games.monopoly.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonopolyGameEngineTest {

    @Test
    void rollShouldSendPlayerToJailWhenLandingOnGoToJailTile() {
        MonopolyGameEngine engine = new MonopolyGameEngine();
        Map<String, Object> state = createState(engine);
        setPlayerPosition(state, 0, 27);

        MonopolyGameEngine.ActionResult result = engine.applyAction(
            state,
            "p1",
            "roll",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            new FixedRandom(0, 1)
        );

        assertTrue(result.success());
        assertEquals(10, playerPosition(result.gameState(), 0));
        assertTrue(Boolean.TRUE.equals(player(result.gameState(), 0).get("inJail")));
        assertEquals("await_end_turn", result.gameState().get("phase"));
        assertNull(result.gameState().get("pendingPurchase"));
    }

    @Test
    void rollShouldSendTaxToFreeParkingPot() {
        MonopolyGameEngine engine = new MonopolyGameEngine();
        Map<String, Object> state = createState(engine);
        setPlayerPosition(state, 0, 1);

        MonopolyGameEngine.ActionResult result = engine.applyAction(
            state,
            "p1",
            "roll",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            new FixedRandom(0, 1)
        );

        assertTrue(result.success());
        assertEquals(4, playerPosition(result.gameState(), 0));
        assertEquals(1380, playerMoney(result.gameState(), 0));
        assertEquals(120, ((Number) result.gameState().get("freeParkingPot")).intValue());
        assertEquals("await_end_turn", result.gameState().get("phase"));
    }

    @Test
    void chanceMoveBackShouldResolveFollowUpSpecialTile() {
        MonopolyGameEngine engine = new MonopolyGameEngine();
        Map<String, Object> state = createState(engine);
        setPlayerPosition(state, 0, 4);
        moveChanceCardToFront(state, "moveBack", "steps", 3);

        MonopolyGameEngine.ActionResult result = engine.applyAction(
            state,
            "p1",
            "roll",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            new FixedRandom(0, 1)
        );

        assertTrue(result.success());
        assertEquals(4, playerPosition(result.gameState(), 0));
        assertEquals(1380, playerMoney(result.gameState(), 0));
        assertEquals(120, ((Number) result.gameState().get("freeParkingPot")).intValue());
        assertEquals("Chance", ((Map<?, ?>) result.gameState().get("lastCard")).get("title"));
        assertEquals("Lui 3 o.", ((Map<?, ?>) result.gameState().get("lastCard")).get("label"));
    }

    private Map<String, Object> createState(MonopolyGameEngine engine) {
        return engine.createInitialState(
            List.of(
                new MonopolyGameEngine.PlayerSeed("p1", "Alice", "dog", 0),
                new MonopolyGameEngine.PlayerSeed("p2", "Bob", "car", 1)
            ),
            1500,
            200,
            new Random(7)
        );
    }

    @SuppressWarnings("unchecked")
    private void moveChanceCardToFront(Map<String, Object> state, String kind, String fieldName, int fieldValue) {
        List<Map<String, Object>> chanceDeck = (List<Map<String, Object>>) state.get("chanceDeck");
        List<Map<String, Object>> reordered = new ArrayList<>(chanceDeck);
        int matchIndex = -1;
        for (int index = 0; index < reordered.size(); index += 1) {
            Map<String, Object> card = reordered.get(index);
            if (kind.equals(card.get("kind")) && fieldValue == ((Number) card.get(fieldName)).intValue()) {
                matchIndex = index;
                break;
            }
        }
        Map<String, Object> matched = reordered.remove(matchIndex);
        reordered.add(0, matched);
        state.put("chanceDeck", reordered);
    }

    @SuppressWarnings("unchecked")
    private void setPlayerPosition(Map<String, Object> state, int playerIndex, int position) {
        ((List<Map<String, Object>>) state.get("players")).get(playerIndex).put("position", position);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> player(Map<String, Object> state, int playerIndex) {
        return ((List<Map<String, Object>>) state.get("players")).get(playerIndex);
    }

    private int playerPosition(Map<String, Object> state, int playerIndex) {
        return ((Number) player(state, playerIndex).get("position")).intValue();
    }

    private int playerMoney(Map<String, Object> state, int playerIndex) {
        return ((Number) player(state, playerIndex).get("money")).intValue();
    }

    private static final class FixedRandom extends Random {
        private final Queue<Integer> values;

        private FixedRandom(Integer... values) {
            this.values = new ArrayDeque<>(List.of(values));
        }

        @Override
        public int nextInt(int bound) {
            Integer next = values.poll();
            if (next == null) {
                return 0;
            }
            return Math.floorMod(next, bound);
        }
    }
}
