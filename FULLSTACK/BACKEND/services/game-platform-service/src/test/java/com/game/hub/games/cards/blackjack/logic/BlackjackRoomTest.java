package com.game.hub.games.cards.blackjack.logic;

import com.game.hub.games.cards.blackjack.model.BlackjackPlayer;
import com.game.hub.games.cards.blackjack.model.Card;
import com.game.hub.games.cards.blackjack.model.Deck;
import com.game.hub.games.cards.blackjack.model.Rank;
import com.game.hub.games.cards.blackjack.model.Suit;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlackjackRoomTest {

    @Test
    void canPlayerActShouldOnlyAllowCurrentTurnPlayer() {
        BlackjackRoom room = seededTurnRoom();

        assertTrue(room.canPlayerAct("p1"));
        assertFalse(room.canPlayerAct("p2"));
    }

    @Test
    void standShouldAdvanceTurnAndFinishRoundAfterLastPlayer() {
        BlackjackRoom room = seededTurnRoom();

        room.playerStand("p1");

        assertEquals("p2", room.getCurrentTurnPlayerId());
        assertFalse(room.canPlayerAct("p1"));
        assertTrue(room.canPlayerAct("p2"));

        room.playerStand("p2");

        assertEquals(BlackjackRoom.GameState.WAITING, room.getGameState());
        assertNull(room.getCurrentTurnPlayerId());
    }

    @Test
    void startRoundShouldSkipPlayerWithNaturalBlackjack() {
        BlackjackRoom room = new BlackjackRoom("BJ-NATURAL");
        room.addPlayer("p1");
        room.addPlayer("p2");
        room.getPlayers().get("p1").placeBet(100);
        room.getPlayers().get("p2").placeBet(100);
        ReflectionTestUtils.setField(room, "deck", new FixedDeck(List.of(
            new Card(Suit.HEARTS, Rank.ACE),
            new Card(Suit.CLUBS, Rank.NINE),
            new Card(Suit.SPADES, Rank.KING),
            new Card(Suit.DIAMONDS, Rank.SEVEN),
            new Card(Suit.HEARTS, Rank.FIVE),
            new Card(Suit.CLUBS, Rank.TEN)
        )));

        room.startRound();

        assertEquals(BlackjackRoom.GameState.PLAYER_TURN, room.getGameState());
        assertEquals("p2", room.getCurrentTurnPlayerId());
        assertFalse(room.canPlayerAct("p1"));
        assertTrue(room.canPlayerAct("p2"));
    }

    @Test
    void surrenderShouldReturnHalfBetAndAdvanceTurn() {
        BlackjackRoom room = seededTurnRoom();
        BlackjackPlayer p1 = room.getPlayers().get("p1");

        assertTrue(room.canPlayerSurrender("p1"));

        room.playerSurrender("p1");

        assertEquals(950, p1.getBalance());
        assertEquals(0, p1.getCurrentBet());
        assertEquals(BlackjackPlayer.RoundResult.SURRENDER, p1.getLastRoundResult());
        assertEquals("p2", room.getCurrentTurnPlayerId());
        assertTrue(room.canPlayerAct("p2"));
    }

    private BlackjackRoom seededTurnRoom() {
        BlackjackRoom room = new BlackjackRoom("BJ-TURN");
        room.addPlayer("p1");
        room.addPlayer("p2");

        BlackjackPlayer p1 = room.getPlayers().get("p1");
        BlackjackPlayer p2 = room.getPlayers().get("p2");
        p1.placeBet(100);
        p2.placeBet(100);
        p1.getHand().addCard(new Card(Suit.HEARTS, Rank.FIVE));
        p1.getHand().addCard(new Card(Suit.CLUBS, Rank.SIX));
        p2.getHand().addCard(new Card(Suit.SPADES, Rank.SEVEN));
        p2.getHand().addCard(new Card(Suit.DIAMONDS, Rank.EIGHT));

        ReflectionTestUtils.setField(room, "gameState", BlackjackRoom.GameState.PLAYER_TURN);
        ReflectionTestUtils.setField(room, "currentTurnPlayerId", "p1");
        return room;
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
