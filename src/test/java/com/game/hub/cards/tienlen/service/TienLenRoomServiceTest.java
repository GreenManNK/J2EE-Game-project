package com.game.hub.cards.tienlen.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TienLenRoomServiceTest {

    @Test
    void shouldLimitRoomToFourPlayers() {
        TienLenRoomService service = new TienLenRoomService(new Random(1));

        assertTrue(service.joinRoom("r1", "u1", "P1", "").ok());
        assertTrue(service.joinRoom("r1", "u2", "P2", "").ok());
        assertTrue(service.joinRoom("r1", "u3", "P3", "").ok());
        assertTrue(service.joinRoom("r1", "u4", "P4", "").ok());

        TienLenRoomService.JoinResult fifth = service.joinRoom("r1", "u5", "P5", "");

        assertFalse(fifth.ok());
        assertEquals("Room is full", fifth.error());
        assertNotNull(fifth.room());
        assertEquals(4, fifth.room().playerCount());
    }

    @Test
    void startGameShouldDealThirteenCardsPerPlayerAndPickThreeSpadesOwner() {
        TienLenRoomService service = new TienLenRoomService(new Random(2));
        joinFour(service, "r2");

        TienLenRoomService.ActionResult start = service.startGame("r2", "u1");

        assertTrue(start.ok());
        assertEquals("GAME_STARTED", start.eventType());
        assertNotNull(start.room());
        assertTrue(start.room().started());
        assertEquals(4, start.room().playerCount());
        assertNotNull(start.room().currentTurnUserId());

        for (String userId : List.of("u1", "u2", "u3", "u4")) {
            TienLenRoomService.PrivateState privateState = service.privateState("r2", userId);
            assertNotNull(privateState);
            assertEquals(13, privateState.hand().size());
        }

        TienLenRoomService.PrivateState firstTurnHand = service.privateState("r2", start.room().currentTurnUserId());
        assertTrue(firstTurnHand.hand().stream().anyMatch(c -> "3S".equals(c.code())));
    }

    @Test
    void threePassesAfterOpeningPlayShouldResetRoundToTrickOwner() {
        TienLenRoomService service = new TienLenRoomService(new Random(3));
        joinFour(service, "r3");
        TienLenRoomService.ActionResult start = service.startGame("r3", "u1");
        assertTrue(start.ok());

        String firstPlayer = start.room().currentTurnUserId();
        TienLenRoomService.ActionResult firstPlay = service.playCards("r3", firstPlayer, List.of("3S"));
        assertTrue(firstPlay.ok());
        assertEquals("PLAYED", firstPlay.eventType());
        assertNotNull(firstPlay.room().currentTrick());

        String p2 = firstPlay.room().currentTurnUserId();
        TienLenRoomService.ActionResult pass1 = service.passTurn("r3", p2);
        assertTrue(pass1.ok());

        String p3 = pass1.room().currentTurnUserId();
        TienLenRoomService.ActionResult pass2 = service.passTurn("r3", p3);
        assertTrue(pass2.ok());

        String p4 = pass2.room().currentTurnUserId();
        TienLenRoomService.ActionResult pass3 = service.passTurn("r3", p4);
        assertTrue(pass3.ok());
        assertEquals("ROUND_RESET", pass3.eventType());
        assertEquals(firstPlayer, pass3.room().currentTurnUserId());
        assertEquals(null, pass3.room().currentTrick());
        assertTrue(pass3.room().passedUserIds().isEmpty());
    }

    @Test
    void leavingDuringMatchShouldContinueGameAndSkipLeaverTurn() {
        TienLenRoomService service = new TienLenRoomService(new Random(4));
        joinFour(service, "r4");
        TienLenRoomService.ActionResult start = service.startGame("r4", "u1");
        assertTrue(start.ok());

        String opener = start.room().currentTurnUserId();
        TienLenRoomService.ActionResult openPlay = service.playCards("r4", opener, List.of("3S"));
        assertTrue(openPlay.ok());
        String leaver = openPlay.room().currentTurnUserId();

        TienLenRoomService.LeaveResult leave = service.leaveRoom("r4", leaver);

        assertTrue(leave.ok());
        assertFalse(leave.roomClosed());
        assertNotNull(leave.room());
        assertTrue(leave.room().started());
        assertEquals(3, leave.room().playerCount());
        assertFalse(leave.room().players().stream().anyMatch(p -> leaver.equals(p.userId())));
        assertNotNull(leave.room().currentTurnUserId());
        assertTrue(leave.room().currentTrick() == null || leave.room().currentTrick().cards() != null);
    }

    @Test
    void resetToWaitingAfterGameShouldKeepPlayersAndClearRunningState() {
        TienLenRoomService service = new TienLenRoomService(new Random(5));
        joinFour(service, "r5");
        TienLenRoomService.ActionResult start = service.startGame("r5", "u1");
        assertTrue(start.ok());

        TienLenRoomService.RoomSnapshot waiting = service.resetToWaitingAfterGame("r5");

        assertNotNull(waiting);
        assertFalse(waiting.started());
        assertFalse(waiting.gameOver());
        assertEquals(4, waiting.playerCount());
        assertEquals(4, waiting.players().size());
        assertEquals(null, waiting.currentTurnUserId());
        for (String userId : List.of("u1", "u2", "u3", "u4")) {
            TienLenRoomService.PrivateState privateState = service.privateState("r5", userId);
            assertNotNull(privateState);
            assertTrue(privateState.hand().isEmpty());
        }
    }

    @Test
    void parseCombinationShouldRecognizeDoubleStraightAndSpecialChopTwoRules() throws Exception {
        TienLenRoomService service = new TienLenRoomService(new Random(6));

        Object doubleStraight3Pairs = parseCombo(service, "3S", "3C", "4S", "4C", "5S", "5C");
        Object doubleStraight4Pairs = parseCombo(service, "6S", "6C", "7S", "7C", "8S", "8C", "9S", "9C");
        Object singleTwo = parseCombo(service, "2S");
        Object pairTwo = parseCombo(service, "2S", "2C");
        Object fourKind = parseCombo(service, "10S", "10C", "10D", "10H");
        Object doubleStraight5Pairs = parseCombo(service, "5S", "5C", "6S", "6C", "7S", "7C", "8S", "8C", "9S", "9C");

        assertNotNull(doubleStraight3Pairs);
        assertEquals("DOUBLE_STRAIGHT", comboType(doubleStraight3Pairs));
        assertEquals(6, comboLength(doubleStraight3Pairs));

        assertTrue(comboCanBeat(fourKind, singleTwo));
        assertTrue(comboCanBeat(doubleStraight3Pairs, singleTwo));
        assertTrue(comboCanBeat(fourKind, pairTwo));
        assertTrue(comboCanBeat(doubleStraight4Pairs, pairTwo));
        assertTrue(comboCanBeat(doubleStraight4Pairs, fourKind));
        assertTrue(comboCanBeat(doubleStraight5Pairs, doubleStraight4Pairs));
    }

    @Test
    void parseCombinationShouldRejectInvalidDoubleStraightContainingTwo() throws Exception {
        TienLenRoomService service = new TienLenRoomService(new Random(7));

        Object invalid = parseCombo(service, "QS", "QC", "KS", "KC", "AS", "AC", "2S", "2C");

        assertEquals(null, invalid);
    }

    @Test
    void autoFillBotsShouldStartGameWhenWaitingTooLong() {
        TienLenRoomService service = new TienLenRoomService(new Random(8));
        assertTrue(service.joinRoom("rb1", "u1", "Human", "").ok());

        TienLenRoomService.AutoFillStartResult result = service.autoFillBotsAndStart("rb1");

        assertTrue(result.changed());
        assertTrue(result.started());
        assertEquals(3, result.addedBotCount());
        assertNotNull(result.room());
        assertTrue(result.room().started());
        assertEquals(4, result.room().playerCount());
        assertEquals(3, result.room().players().stream().filter(TienLenRoomService.PlayerSnapshot::bot).count());
        assertNotNull(service.privateState("rb1", "u1"));
        assertEquals(13, service.privateState("rb1", "u1").hand().size());
    }

    @Test
    void botTakeTurnShouldProduceValidActionWhenBotTurn() {
        TienLenRoomService service = new TienLenRoomService(new Random(9));
        assertTrue(service.joinRoom("rb2", "u1", "Human", "").ok());

        TienLenRoomService.AutoFillStartResult start = service.autoFillBotsAndStart("rb2");
        assertTrue(start.started());

        TienLenRoomService.RoomSnapshot room = start.room();
        assertNotNull(room);

        if ("u1".equals(room.currentTurnUserId())) {
            TienLenRoomService.ActionResult humanOpen = service.playCards("rb2", "u1", List.of("3S"));
            assertTrue(humanOpen.ok());
            room = humanOpen.room();
        }

        assertNotNull(room);
        assertNotNull(room.currentTurnUserId());
        String currentTurnUserId = room.currentTurnUserId();
        assertTrue(room.players().stream().anyMatch(p -> p.userId().equals(currentTurnUserId) && p.bot()));

        TienLenRoomService.ActionResult botAction = service.botTakeTurn("rb2");

        assertTrue(botAction.ok());
        assertNotNull(botAction.room());
        assertTrue(List.of("PLAYED", "PASSED", "ROUND_RESET", "GAME_OVER").contains(botAction.eventType()));
    }

    @Test
    void resetToWaitingAfterGameShouldRemoveBotsAndKeepHumans() {
        TienLenRoomService service = new TienLenRoomService(new Random(10));
        assertTrue(service.joinRoom("rb3", "u1", "Human", "").ok());
        TienLenRoomService.AutoFillStartResult start = service.autoFillBotsAndStart("rb3");
        assertTrue(start.started());

        TienLenRoomService.RoomSnapshot waiting = service.resetToWaitingAfterGame("rb3");

        assertNotNull(waiting);
        assertFalse(waiting.started());
        assertEquals(1, waiting.playerCount());
        assertEquals(1, waiting.players().size());
        assertEquals("u1", waiting.players().getFirst().userId());
        assertFalse(waiting.players().stream().anyMatch(TienLenRoomService.PlayerSnapshot::bot));
    }

    private static void joinFour(TienLenRoomService service, String roomId) {
        assertTrue(service.joinRoom(roomId, "u1", "P1", "").ok());
        assertTrue(service.joinRoom(roomId, "u2", "P2", "").ok());
        assertTrue(service.joinRoom(roomId, "u3", "P3", "").ok());
        assertTrue(service.joinRoom(roomId, "u4", "P4", "").ok());
    }

    private static Object parseCombo(TienLenRoomService service, String... codes) throws Exception {
        Method parse = TienLenRoomService.class.getDeclaredMethod("parseCombination", List.class);
        parse.setAccessible(true);
        List<TienLenCard> cards = java.util.Arrays.stream(codes)
            .map(TienLenCard::parseCode)
            .toList();
        return parse.invoke(service, cards);
    }

    private static String comboType(Object combo) throws Exception {
        Method type = combo.getClass().getDeclaredMethod("type");
        type.setAccessible(true);
        Object enumValue = type.invoke(combo);
        return String.valueOf(enumValue);
    }

    private static int comboLength(Object combo) throws Exception {
        Method length = combo.getClass().getDeclaredMethod("length");
        length.setAccessible(true);
        return (int) length.invoke(combo);
    }

    private static boolean comboCanBeat(Object candidate, Object current) throws Exception {
        Method canBeat = candidate.getClass().getDeclaredMethod("canBeat", candidate.getClass());
        canBeat.setAccessible(true);
        return (boolean) canBeat.invoke(candidate, current);
    }
}
