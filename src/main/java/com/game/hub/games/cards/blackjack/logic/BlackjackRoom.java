package com.game.hub.games.cards.blackjack.logic;

import com.game.hub.games.cards.blackjack.model.BlackjackPlayer;
import com.game.hub.games.cards.blackjack.model.Deck;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class BlackjackRoom {
    private final String id;
    private final Map<String, BlackjackPlayer> players = new ConcurrentHashMap<>();
    private final List<String> spectators = new ArrayList<>();
    private final Set<String> stoodPlayers = ConcurrentHashMap.newKeySet();
    private final Dealer dealer = new Dealer();
    private final Deck deck = new Deck();
    private GameState gameState = GameState.WAITING;

    public BlackjackRoom(String id) {
        this.id = id;
    }

    public void addPlayer(String playerId) {
        if (players.size() < 5) {
            players.put(playerId, new BlackjackPlayer(playerId, 1000));
        }
    }
    
    public void addSpectator(String spectatorId) {
        if (spectators.size() < 4) {
            spectators.add(spectatorId);
        }
    }

    public void removePlayer(String playerId) {
        players.remove(playerId);
        stoodPlayers.remove(playerId);
        spectators.remove(playerId);
    }

    public void startRound() {
        if (gameState != GameState.WAITING) {
            return;
        }
        if (!hasAnyBetPlaced()) {
            return;
        }
        deck.reset();
        deck.shuffle();
        stoodPlayers.clear();
        dealer.getHand().clear();
        for (BlackjackPlayer player : players.values()) {
            player.getHand().clear();
            if (player.hasActiveBet()) {
                player.getHand().addCard(deck.deal());
                player.getHand().addCard(deck.deal());
            } else {
                stoodPlayers.add(player.getId());
            }
        }
        dealer.getHand().addCard(deck.deal());
        dealer.getHand().addCard(deck.deal());
        gameState = GameState.PLAYER_TURN;
    }

    public void playerHit(String playerId) {
        if (!canPlayerAct(playerId)) {
            return;
        }
        BlackjackPlayer player = players.get(playerId);
        if (player == null) {
            return;
        }
        player.getHand().addCard(deck.deal());
        if (player.getHand().getValue() > 21) {
            stoodPlayers.add(playerId);
        }
        advanceRoundIfNeeded();
    }

    public void playerStand(String playerId) {
        if (!canPlayerAct(playerId)) {
            return;
        }
        stoodPlayers.add(playerId);
        advanceRoundIfNeeded();
    }

    public void dealerTurn() {
        gameState = GameState.DEALER_TURN;
        while (dealer.shouldHit(new ArrayList<>(players.values()))) {
            dealer.getHand().addCard(deck.deal());
        }
        endRound();
    }

    private void endRound() {
        int dealerValue = dealer.getHand().getValue();
        boolean dealerBlackjack = isNaturalBlackjack(dealer);
        for (BlackjackPlayer player : players.values()) {
            if (!player.hasActiveBet()) {
                continue;
            }
            int playerValue = player.getHand().getValue();
            boolean playerBlackjack = isNaturalBlackjack(player);
            if (playerBlackjack && !dealerBlackjack) {
                player.blackjackWin();
            } else if (dealerBlackjack && playerBlackjack) {
                player.push();
            } else if (dealerBlackjack) {
                player.loseBet();
            } else if (playerValue > 21) {
                player.loseBet();
            } else if (dealerValue > 21 || playerValue > dealerValue) {
                player.winBet();
            } else if (playerValue < dealerValue) {
                player.loseBet();
            } else {
                player.push();
            }
        }
        stoodPlayers.clear();
        gameState = GameState.WAITING;
    }

    private void advanceRoundIfNeeded() {
        if (players.isEmpty()) {
            return;
        }
        boolean allResolved = players.values().stream()
            .allMatch(player -> stoodPlayers.contains(player.getId()) || player.getHand().getValue() > 21);
        if (allResolved) {
            dealerTurn();
        }
    }

    public boolean hasAnyBetPlaced() {
        return players.values().stream().anyMatch(player -> player.getCurrentBet() > 0);
    }

    public boolean canPlayerAct(String playerId) {
        if (gameState != GameState.PLAYER_TURN) {
            return false;
        }
        BlackjackPlayer player = players.get(playerId);
        if (player == null) {
            return false;
        }
        if (!player.hasActiveBet()) {
            return false;
        }
        if (stoodPlayers.contains(playerId)) {
            return false;
        }
        return player.getHand().getValue() <= 21;
    }

    public boolean canPlayerDouble(String playerId) {
        BlackjackPlayer player = players.get(playerId);
        if (!canPlayerAct(playerId) || player == null) {
            return false;
        }
        return player.getHand().getCards().size() == 2 && player.getBalance() >= player.getCurrentBet();
    }

    private boolean isNaturalBlackjack(BlackjackPlayer player) {
        return player.getHand().getCards().size() == 2 && player.getHand().getValue() == 21;
    }

    public String getId() {
        return id;
    }

    public Map<String, BlackjackPlayer> getPlayers() {
        return players;
    }
    
    public List<String> getSpectators() {
        return spectators;
    }

    public Dealer getDealer() {
        return dealer;
    }

    public GameState getGameState() {
        return gameState;
    }

    public enum GameState {
        WAITING, PLAYER_TURN, DEALER_TURN
    }
}
