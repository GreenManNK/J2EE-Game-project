package com.game.hub.games.puzzle.service;

import com.game.hub.games.puzzle.model.Word;
import com.game.hub.games.puzzle.repository.WordRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Service
public class WordPuzzleService {
    private static final int MIN_WORD_LENGTH = 3;
    private static final int MAX_WORD_LENGTH = 12;
    private static final List<String> DEFAULT_WORD_POOL = List.of(
        "JAVA", "SPRING", "PUZZLE", "GAME", "SOCKET", "PLAYER", "SCORE", "RACE"
    );

    private final WordRepository repository;

    public WordPuzzleService(WordRepository repository) {
        this.repository = repository;
    }

    public List<Word> listWords() {
        return repository.findAll(Sort.by(Sort.Direction.ASC, "word"));
    }

    public List<String> getWordPool() {
        LinkedHashSet<String> pool = new LinkedHashSet<>();
        for (Word word : listWords()) {
            String normalized = normalizeWord(word == null ? null : word.getWord());
            if (normalized != null) {
                pool.add(normalized);
            }
        }
        if (pool.size() < DEFAULT_WORD_POOL.size()) {
            pool.addAll(DEFAULT_WORD_POOL);
        }
        return new ArrayList<>(pool);
    }

    public Word addWord(String rawWord) {
        String normalized = normalizeWord(rawWord);
        if (normalized == null) {
            throw new IllegalArgumentException("Tu moi phai dai 3-12 ky tu, khong co khoang trang va chi gom chu/so.");
        }
        if (repository.existsByWordIgnoreCase(normalized)) {
            throw new IllegalArgumentException("Tu nay da ton tai trong database.");
        }
        return repository.save(new Word(normalized));
    }

    private String normalizeWord(String rawWord) {
        if (rawWord == null) {
            return null;
        }
        String collapsed = rawWord.trim().replaceAll("\\s+", "");
        if (collapsed.isEmpty()) {
            return null;
        }
        String normalized = collapsed.toUpperCase(Locale.ROOT);
        int length = normalized.codePointCount(0, normalized.length());
        if (length < MIN_WORD_LENGTH || length > MAX_WORD_LENGTH) {
            return null;
        }
        boolean valid = normalized.codePoints().allMatch(Character::isLetterOrDigit);
        return valid ? normalized : null;
    }
}
