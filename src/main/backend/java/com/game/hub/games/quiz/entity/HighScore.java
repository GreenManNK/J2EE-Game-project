package com.game.hub.games.quiz.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class HighScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String playerName;
    private int score;

    public HighScore() {
    }

    public HighScore(String playerName, int score) {
        this.playerName = playerName;
        this.score = score;
    }

    // Getters and setters
}
