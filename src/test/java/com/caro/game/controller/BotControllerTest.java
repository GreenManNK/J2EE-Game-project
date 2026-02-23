package com.caro.game.controller;

import com.caro.game.logic.BotEasy;
import com.caro.game.logic.BotHard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
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
        assertEquals(5, countPlayerMoves(state));
    }

    private static Object newBotSessionState() throws Exception {
        Class<?> stateClass = Class.forName("com.caro.game.controller.BotController$BotSessionState");
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
