package com.game.hub.games.puzzle.service;

import com.game.hub.games.puzzle.model.Sudoku;
import com.game.hub.games.puzzle.repository.SudokuRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SudokuService {

    private final SudokuRepository repository;

    @Autowired
    public SudokuService(SudokuRepository repository) {
        this.repository = repository;
    }

    public List<Sudoku> getAllPuzzles() {
        return repository.findAll();
    }
}
