package com.game.hub.games.caro.controller;

import com.game.hub.games.caro.logic.BotEasy;
import com.game.hub.games.caro.logic.BotHard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotControllerTest {

    @AfterEach
    void resetBots() {
        BotEasy.resetBoard();
        BotHard.resetBoard();
    }

    @Test
    void easyMoveShouldReturnPlayerWinWithoutThrowingWhenResponseContainsNullCoordinates() throws Exception {
        BotController controller = new BotController();
        MockHttpSession session = new MockHttpSession();
        Object state = newBotSessionState();

        addPlayerMove(state, 7, 7);
        addPlayerMove(state, 7, 8);
        addPlayerMove(state, 7, 9);
        addPlayerMove(state, 7, 10);
        mark(state, 7, 7, 'X');
        mark(state, 7, 8, 'X');
        mark(state, 7, 9, 'X');
        mark(state, 7, 10, 'X');

        session.setAttribute("BOT_EASY_STATE", state);

        Map<String, Object> result = controller.easyMove(new BotController.MoveRequest(7, 11), session);

        assertTrue(result.get("playerWin") instanceof Boolean b && b);
        assertFalse(result.get("botWin") instanceof Boolean b && b);
        assertNull(result.get("x"));
        assertNull(result.get("y"));
        assertTrue(result.get("winLine") instanceof List<?> line && line.size() >= 5);
        assertEquals(5, countPlayerMoves(state));
    }

    @Test
    void easyMoveShouldReturnDrawWhenBoardBecomesFullAfterPlayerMove() throws Exception {
        BotController controller = new BotController();
        MockHttpSession session = new MockHttpSession();
        Object state = newBotSessionState();
        int emptyX = 14;
        int emptyY = 14;

        for (int x = 0; x < 15; x++) {
            for (int y = 0; y < 15; y++) {
                if (x == emptyX && y == emptyY) {
                    continue;
                }
                mark(state, x, y, ((x + y) % 2 == 0) ? 'X' : 'O');
            }
        }

        session.setAttribute("BOT_EASY_STATE", state);

        Map<String, Object> result = controller.easyMove(new BotController.MoveRequest(emptyX, emptyY), session);

        assertTrue(result.get("success") instanceof Boolean b && b);
        assertTrue(result.get("draw") instanceof Boolean b && b);
        assertFalse(result.get("playerWin") instanceof Boolean b && b);
        assertFalse(result.get("botWin") instanceof Boolean b && b);
        assertNull(result.get("x"));
        assertNull(result.get("y"));
    }

    private static Object newBotSessionState() throws Exception {
        Class<?> stateClass = Class.forName("com.game.hub.games.caro.controller.BotController$BotSessionState");
        Constructor<?> ctor = stateClass.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    private static void addPlayerMove(Object state, int x, int y) throws Exception {
        invoke(state, "addPlayerMove", new Class<?>[]{int.class, int.class}, x, y);
    }

    private static void mark(Object state, int x, int y, char piece) throws Exception {
        invoke(state, "mark", new Class<?>[]{int.class, int.class, char.class}, x, y, piece);
    }

    private static int countPlayerMoves(Object state) throws Exception {
        Class<?> stateClass = state.getClass();
        var field = stateClass.getDeclaredField("playerMoves");
        field.setAccessible(true);
        return ((java.util.List<?>) field.get(state)).size();
    }

    private static Object invoke(Object target, String methodName, Class<?>[] paramTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, paramTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }
}
