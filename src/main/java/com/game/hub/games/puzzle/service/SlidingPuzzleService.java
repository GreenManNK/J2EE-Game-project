package com.game.hub.games.puzzle.service;

import com.game.hub.games.puzzle.model.SlidingPuzzle;
import com.game.hub.games.puzzle.repository.SlidingPuzzleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SlidingPuzzleService {

    private final SlidingPuzzleRepository repository;

    @Autowired
    public SlidingPuzzleService(SlidingPuzzleRepository repository) {
        this.repository = repository;
    }

    public List<SlidingPuzzle> getAllPuzzles() {
        return repository.findAll();
    }
}
