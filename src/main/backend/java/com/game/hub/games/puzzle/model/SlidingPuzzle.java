package com.game.hub.games.puzzle.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class SlidingPuzzle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String imageUrl;
    private int gridSize;

    public SlidingPuzzle() {}

    public SlidingPuzzle(String imageUrl, int gridSize) {
        this.imageUrl = imageUrl;
        this.gridSize = gridSize;
    }

    public Long getId() {
        return id;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public int getGridSize() {
        return gridSize;
    }
}
