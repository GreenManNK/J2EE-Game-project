package com.game.hub.games.cards.blackjack.websocket;

import com.game.hub.games.cards.blackjack.logic.Dealer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.hub.games.cards.blackjack.logic.BlackjackRoom;
import com.game.hub.games.cards.blackjack.model.Card;
import com.game.hub.games.cards.blackjack.model.BlackjackPlayer;
import com.game.hub.games.cards.blackjack.model.Deck;
import com.game.hub.games.cards.blackjack.model.Rank;
import com.game.hub.games.cards.blackjack.model.Suit;
import com.game.hub.games.cards.blackjack.service.BlackjackService;
import com.game.hub.service.AchievementService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlackjackSocketTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createBetAndCloseShouldBroadcastPlayerTurnAndRemoveRoomWhenEmpty() throws Exception {
        BlackjackService blackjackService = new BlackjackService();
        AchievementService achievementService = mock(AchievementService.class);

        BlackjackSocket socket = new BlackjackSocket();
        ReflectionTestUtils.setField(socket, "blackjackService", blackjackService);
        ReflectionTestUtils.setField(socket, "achievementService", achievementService);

        WebSocketSession session = session("blackjack-session-1", "blackjack-player");

        socket.handleTextMessage(session, new TextMessage("{\"action\":\"create\"}"));
        BlackjackRoom room = blackjackService.getAvailableRooms().stream().findFirst().orElseThrow();
        ReflectionTestUtils.setField(room, "deck", new FixedDeck(List.of(
            new Card(Suit.HEARTS, Rank.FIVE),
            new Card(Suit.CLUBS, Rank.SIX),
            new Card(Suit.SPADES, Rank.SEVEN),
            new Card(Suit.DIAMONDS, Rank.EIGHT),
            new Card(Suit.HEARTS, Rank.TWO)
        )));

        socket.handleTextMessage(session, new TextMessage("{\"action\":\"bet\",\"amount\":100}"));

        ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeastOnce()).sendMessage(messages.capture());
        List<TextMessage> payloads = messages.getAllValues();
        Map<String, Object> latestPayload = payload(payloads.get(payloads.size() - 1));
        assertEquals(room.getId(), latestPayload.get("id"));
        assertEquals("PLAYER_TURN", String.valueOf(latestPayload.get("gameState")));
        assertEquals(1, ((Number) latestPayload.get("playerCount")).intValue());
        assertEquals("blackjack-player", latestPayload.get("yourId"));
        assertNotNull(blackjackService.getRoom(room.getId()));

        socket.afterConnectionClosed(session, CloseStatus.NORMAL);
        assertNull(blackjackService.getRoom(room.getId()));
    }

    @Test
    void spectateUnknownRoomShouldSendError() throws Exception {
        BlackjackService blackjackService = new BlackjackService();
        AchievementService achievementService = mock(AchievementService.class);

        BlackjackSocket socket = new BlackjackSocket();
        ReflectionTestUtils.setField(socket, "blackjackService", blackjackService);
        ReflectionTestUtils.setField(socket, "achievementService", achievementService);

        WebSocketSession session = session("blackjack-session-2", "blackjack-viewer");

        socket.handleTextMessage(session, new TextMessage("{\"action\":\"spectate\",\"roomId\":\"missing-room\"}"));

        ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(messages.capture());
        assertTrue(messages.getValue().getPayload().contains("\"error\":\"Room not found\""));
    }

    @Test
    void standShouldAwardBlackjackAchievementToRoundWinner() throws Exception {
        BlackjackService blackjackService = mock(BlackjackService.class);
        AchievementService achievementService = mock(AchievementService.class);
        BlackjackRoom room = mock(BlackjackRoom.class);

        BlackjackSocket socket = new BlackjackSocket();
        ReflectionTestUtils.setField(socket, "blackjackService", blackjackService);
        ReflectionTestUtils.setField(socket, "achievementService", achievementService);

        WebSocketSession session = session("blackjack-session-3", "blackjack-player");
        BlackjackPlayer player = new BlackjackPlayer("blackjack-player", 1000);

        when(room.getId()).thenReturn("BJ-WIN");
        when(room.canPlayerAct("blackjack-player")).thenReturn(true);
        when(room.getPlayers()).thenReturn(Map.of("blackjack-player", player));
        when(room.getSpectators()).thenReturn(List.of());
        when(room.getDealer()).thenReturn(new Dealer());
        when(room.getGameState()).thenReturn(BlackjackRoom.GameState.WAITING);
        when(room.getWinningPlayerIds()).thenReturn(List.of("blackjack-player"));

        @SuppressWarnings("unchecked")
        Map<WebSocketSession, BlackjackRoom> sessionToRoomMap =
            (Map<WebSocketSession, BlackjackRoom>) ReflectionTestUtils.getField(socket, "sessionToRoomMap");
        @SuppressWarnings("unchecked")
        Map<String, Set<WebSocketSession>> roomToSessionsMap =
            (Map<String, Set<WebSocketSession>>) ReflectionTestUtils.getField(socket, "roomToSessionsMap");
        @SuppressWarnings("unchecked")
        Map<WebSocketSession, String> sessionPlayerIds =
            (Map<WebSocketSession, String>) ReflectionTestUtils.getField(socket, "sessionPlayerIds");

        assertNotNull(sessionToRoomMap);
        assertNotNull(roomToSessionsMap);
        assertNotNull(sessionPlayerIds);

        sessionToRoomMap.put(session, room);
        roomToSessionsMap.put("BJ-WIN", java.util.concurrent.ConcurrentHashMap.newKeySet());
        roomToSessionsMap.get("BJ-WIN").add(session);
        sessionPlayerIds.put(session, "blackjack-player");

        socket.handleTextMessage(session, new TextMessage("{\"action\":\"stand\"}"));

        verify(achievementService).checkAndAward("blackjack-player", "Blackjack", true);
        verify(room).clearRoundOutcomes();
    }

    private WebSocketSession session(String sessionId, String principalName) {
        WebSocketSession session = mock(WebSocketSession.class);
        Principal principal = () -> principalName;
        when(session.getId()).thenReturn(sessionId);
        when(session.getPrincipal()).thenReturn(principal);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private Map<String, Object> payload(TextMessage message) throws Exception {
        return objectMapper.readValue(message.getPayload(), new TypeReference<>() {});
    }

    private static final class FixedDeck extends Deck {
        private List<Card> scriptedCards;

        private FixedDeck(List<Card> scriptedCards) {
            this.scriptedCards = new ArrayList<>(scriptedCards);
        }

        @Override
        public void reset() {
        }

        @Override
        public void shuffle() {
        }

        @Override
        public Card deal() {
            return scriptedCards.remove(0);
        }
    }
}
