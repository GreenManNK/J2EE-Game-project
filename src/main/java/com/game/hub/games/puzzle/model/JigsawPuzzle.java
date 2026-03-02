package com.game.hub.games.puzzle.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class JigsawPuzzle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String imageUrl;
    private int pieces;

    public JigsawPuzzle() {}

    public JigsawPuzzle(String imageUrl, int pieces) {
        this.imageUrl = imageUrl;
        this.pieces = pieces;
    }

    public Long getId() {
        return id;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public int getPieces() {
        return pieces;
    }
}
