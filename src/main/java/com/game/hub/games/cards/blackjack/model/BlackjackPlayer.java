package com.game.hub.games.cards.blackjack.model;

public class BlackjackPlayer {
    private final String id;
    private final Hand hand = new Hand();
    private int balance;
    private int currentBet;
    private int lastBet;

    public BlackjackPlayer(String id, int initialBalance) {
        this.id = id;
        this.balance = initialBalance;
    }

    public String getId() {
        return id;
    }

    public Hand getHand() {
        return hand;
    }

    public int getBalance() {
        return balance;
    }

    public int getCurrentBet() {
        return currentBet;
    }

    public int getLastBet() {
        return lastBet;
    }

    public boolean hasActiveBet() {
        return currentBet > 0;
    }

    public void placeBet(int amount) {
        if (amount > balance) {
            throw new IllegalArgumentException("Insufficient balance");
        }
        currentBet = amount;
        lastBet = amount;
        balance -= amount;
    }

    public void placeAdditionalBet(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Invalid bet amount");
        }
        if (amount > balance) {
            throw new IllegalArgumentException("Insufficient balance");
        }
        currentBet += amount;
        balance -= amount;
    }

    public void winBet() {
        balance += currentBet * 2;
        currentBet = 0;
    }

    public void blackjackWin() {
        balance += (currentBet * 5) / 2;
        currentBet = 0;
    }

    public void loseBet() {
        currentBet = 0;
    }

    public void push() {
        balance += currentBet;
        currentBet = 0;
    }
}
