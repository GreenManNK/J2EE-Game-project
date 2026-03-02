package com.game.hub.games.puzzle.service;

import com.game.hub.games.puzzle.model.JigsawPuzzle;
import com.game.hub.games.puzzle.repository.JigsawPuzzleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class JigsawPuzzleService {

    private final JigsawPuzzleRepository repository;

    @Autowired
    public JigsawPuzzleService(JigsawPuzzleRepository repository) {
        this.repository = repository;
    }

    public List<JigsawPuzzle> getAllPuzzles() {
        return repository.findAll();
    }
}
