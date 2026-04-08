package com.game.hub.games.puzzle.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Sudoku {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String puzzle; // 81 characters, 0 for empty cells
    private String solution;

    public Sudoku() {}

    public Sudoku(String puzzle, String solution) {
        this.puzzle = puzzle;
        this.solution = solution;
    }

    public Long getId() {
        return id;
    }

    public String getPuzzle() {
        return puzzle;
    }

    public String getSolution() {
        return solution;
    }
}
