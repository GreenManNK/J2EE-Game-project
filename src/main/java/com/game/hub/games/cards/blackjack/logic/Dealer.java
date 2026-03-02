package com.game.hub.games.cards.blackjack.logic;

import com.game.hub.games.cards.blackjack.model.BlackjackPlayer;
import java.util.List;

public class Dealer extends BlackjackPlayer {
    public Dealer() {
        super("dealer", 1000000);
    }

    public boolean shouldHit(List<BlackjackPlayer> players) {
        return getHand().getValue() < 17;
    }
}
