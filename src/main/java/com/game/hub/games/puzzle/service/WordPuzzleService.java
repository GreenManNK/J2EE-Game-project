package com.game.hub.games.puzzle.service;

import com.game.hub.games.puzzle.model.Word;
import com.game.hub.games.puzzle.repository.WordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WordPuzzleService {

    private final WordRepository repository;

    @Autowired
    public WordPuzzleService(WordRepository repository) {
        this.repository = repository;
    }

    public List<Word> getAllWords() {
        return repository.findAll();
    }
}
