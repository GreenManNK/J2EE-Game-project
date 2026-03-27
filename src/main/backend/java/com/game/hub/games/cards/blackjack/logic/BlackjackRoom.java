package com.game.hub.games.cards.blackjack.logic;

import com.game.hub.games.cards.blackjack.model.BlackjackPlayer;
import com.game.hub.games.cards.blackjack.model.Deck;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class BlackjackRoom {
    private static final int PLAYER_LIMIT = 5;
    private static final int SPECTATOR_LIMIT = 4;

    private final String id;
    private final Map<String, BlackjackPlayer> players = new ConcurrentHashMap<>();
    private final Set<String> spectators = ConcurrentHashMap.newKeySet();
    private final Set<String> stoodPlayers = ConcurrentHashMap.newKeySet();
    private final List<String> seatOrder = new ArrayList<>();
    private final Dealer dealer = new Dealer();
    private Deck deck;
    private GameState gameState = GameState.WAITING;
    private String currentTurnPlayerId;

    public BlackjackRoom(String id) {
        this(id, new Deck());
    }

    public BlackjackRoom(String id, Deck deck) {
        this.id = id;
        this.deck = deck == null ? new Deck() : deck;
    }

    public JoinResult addPlayer(String playerId) {
        if (playerId == null || playerId.isBlank()) {
            return JoinResult.ROOM_FULL;
        }
        if (players.containsKey(playerId)) {
            return JoinResult.ALREADY_IN_ROOM;
        }
        if (players.size() >= PLAYER_LIMIT) {
            return JoinResult.ROOM_FULL;
        }
        players.put(playerId, new BlackjackPlayer(playerId, 1000));
        seatOrder.add(playerId);
        spectators.remove(playerId);
        return JoinResult.JOINED;
    }
    
    public JoinResult addSpectator(String spectatorId) {
        if (spectatorId == null || spectatorId.isBlank()) {
            return JoinResult.ROOM_FULL;
        }
        if (players.containsKey(spectatorId) || spectators.contains(spectatorId)) {
            return JoinResult.ALREADY_IN_ROOM;
        }
        if (spectators.size() >= SPECTATOR_LIMIT) {
            return JoinResult.ROOM_FULL;
        }
        spectators.add(spectatorId);
        return JoinResult.JOINED;
    }

    public void removePlayer(String playerId) {
        players.remove(playerId);
        stoodPlayers.remove(playerId);
        spectators.remove(playerId);
        seatOrder.remove(playerId);
        if (playerId != null && playerId.equals(currentTurnPlayerId)) {
            currentTurnPlayerId = nextActingPlayerAfter(playerId);
            if (gameState == GameState.PLAYER_TURN && currentTurnPlayerId == null) {
                dealerTurn();
            }
        }
    }

    public void removeSpectator(String spectatorId) {
        spectators.remove(spectatorId);
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
        currentTurnPlayerId = null;
        dealer.getHand().clear();
        for (String playerId : seatOrder) {
            BlackjackPlayer player = players.get(playerId);
            if (player == null) {
                continue;
            }
            player.clearRoundOutcome();
            player.getHand().clear();
            if (!player.hasActiveBet()) {
                stoodPlayers.add(player.getId());
                continue;
            }
            player.getHand().addCard(deck.deal());
        }
        for (String playerId : seatOrder) {
            BlackjackPlayer player = players.get(playerId);
            if (player == null) {
                continue;
            }
            if (player.hasActiveBet()) {
                player.getHand().addCard(deck.deal());
            }
        }
        dealer.getHand().addCard(deck.deal());
        dealer.getHand().addCard(deck.deal());
        markAutoResolvedHands();
        if (isNaturalBlackjack(dealer)) {
            currentTurnPlayerId = null;
            endRound();
            return;
        }
        currentTurnPlayerId = firstActingPlayer();
        if (currentTurnPlayerId == null) {
            dealerTurn();
            return;
        }
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
            advanceRoundIfNeeded();
        }
    }

    public void playerStand(String playerId) {
        if (!canPlayerAct(playerId)) {
            return;
        }
        stoodPlayers.add(playerId);
        advanceRoundIfNeeded();
    }

    public void playerSurrender(String playerId) {
        if (!canPlayerSurrender(playerId)) {
            return;
        }
        BlackjackPlayer player = players.get(playerId);
        if (player == null) {
            return;
        }
        player.surrender();
        stoodPlayers.add(playerId);
        advanceRoundIfNeeded();
    }

    public void dealerTurn() {
        gameState = GameState.DEALER_TURN;
        currentTurnPlayerId = null;
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
        currentTurnPlayerId = null;
        gameState = GameState.WAITING;
    }

    private void advanceRoundIfNeeded() {
        if (players.isEmpty()) {
            return;
        }
        currentTurnPlayerId = nextActingPlayerAfter(currentTurnPlayerId);
        if (currentTurnPlayerId == null) {
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
        if (currentTurnPlayerId == null || !currentTurnPlayerId.equals(playerId)) {
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

    public boolean canPlayerSurrender(String playerId) {
        BlackjackPlayer player = players.get(playerId);
        if (!canPlayerAct(playerId) || player == null) {
            return false;
        }
        return player.getHand().getCards().size() == 2 && player.getCurrentBet() > 0;
    }

    private boolean isNaturalBlackjack(BlackjackPlayer player) {
        return player.getHand().getCards().size() == 2 && player.getHand().getValue() == 21;
    }

    private void markAutoResolvedHands() {
        for (String playerId : seatOrder) {
            BlackjackPlayer player = players.get(playerId);
            if (player == null || !player.hasActiveBet()) {
                continue;
            }
            if (isNaturalBlackjack(player)) {
                stoodPlayers.add(playerId);
            }
        }
    }

    public String getId() {
        return id;
    }

    public Map<String, BlackjackPlayer> getPlayers() {
        return players;
    }

    public List<String> getSpectators() {
        return new ArrayList<>(spectators);
    }

    public int getPlayerLimit() {
        return PLAYER_LIMIT;
    }

    public int getSpectatorLimit() {
        return SPECTATOR_LIMIT;
    }

    public List<String> getWinningPlayerIds() {
        return players.values().stream()
            .filter(BlackjackPlayer::wonLastRound)
            .map(BlackjackPlayer::getId)
            .toList();
    }

    public void clearRoundOutcomes() {
        players.values().forEach(BlackjackPlayer::clearRoundOutcome);
    }

    public Dealer getDealer() {
        return dealer;
    }

    public GameState getGameState() {
        return gameState;
    }

    public String getCurrentTurnPlayerId() {
        return currentTurnPlayerId;
    }

    private String firstActingPlayer() {
        for (String playerId : seatOrder) {
            if (isEligibleTurnPlayer(playerId)) {
                return playerId;
            }
        }
        return null;
    }

    private String nextActingPlayerAfter(String currentPlayerId) {
        if (seatOrder.isEmpty()) {
            return null;
        }
        int startIndex = currentPlayerId == null ? -1 : seatOrder.indexOf(currentPlayerId);
        for (int step = 1; step <= seatOrder.size(); step++) {
            int index = startIndex + step;
            if (index < 0) {
                index = step - 1;
            }
            String candidateId = seatOrder.get(index % seatOrder.size());
            if (isEligibleTurnPlayer(candidateId)) {
                return candidateId;
            }
        }
        return null;
    }

    private boolean isEligibleTurnPlayer(String playerId) {
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

    public enum GameState {
        WAITING, PLAYER_TURN, DEALER_TURN
    }

    public enum JoinResult {
        JOINED,
        ALREADY_IN_ROOM,
        ROOM_FULL
    }
}
