package com.game.hub.games.cards.blackjack.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FixedBlackjackDeck extends Deck {
    private List<Card> scriptedCards = new ArrayList<>();

    public FixedBlackjackDeck() {
        super();
        reset();
    }

    @Override
    public void reset() {
        List<Card> nextCards = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // Keep the opening hands deterministic for integration tests.
        add(nextCards, seen, Suit.HEARTS, Rank.FIVE);
        add(nextCards, seen, Suit.SPADES, Rank.SEVEN);
        add(nextCards, seen, Suit.CLUBS, Rank.SIX);
        add(nextCards, seen, Suit.DIAMONDS, Rank.EIGHT);
        add(nextCards, seen, Suit.HEARTS, Rank.FOUR);
        add(nextCards, seen, Suit.CLUBS, Rank.THREE);
        add(nextCards, seen, Suit.DIAMONDS, Rank.FOUR);
        add(nextCards, seen, Suit.HEARTS, Rank.SIX);
        add(nextCards, seen, Suit.SPADES, Rank.FIVE);
        add(nextCards, seen, Suit.CLUBS, Rank.SEVEN);
        add(nextCards, seen, Suit.DIAMONDS, Rank.NINE);
        add(nextCards, seen, Suit.SPADES, Rank.TWO);
        add(nextCards, seen, Suit.HEARTS, Rank.NINE);
        add(nextCards, seen, Suit.CLUBS, Rank.TWO);
        add(nextCards, seen, Suit.DIAMONDS, Rank.THREE);
        add(nextCards, seen, Suit.SPADES, Rank.FOUR);

        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                add(nextCards, seen, suit, rank);
            }
        }
        scriptedCards = nextCards;
    }

    @Override
    public void shuffle() {
    }

    @Override
    public Card deal() {
        if (scriptedCards.isEmpty()) {
            reset();
        }
        return scriptedCards.remove(0);
    }

    private void add(List<Card> cards, Set<String> seen, Suit suit, Rank rank) {
        String key = suit.name() + ":" + rank.name();
        if (seen.add(key)) {
            cards.add(new Card(suit, rank));
        }
    }
}
