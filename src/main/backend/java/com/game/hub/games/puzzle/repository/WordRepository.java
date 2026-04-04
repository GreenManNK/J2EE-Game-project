package com.game.hub.games.puzzle.repository;

import com.game.hub.games.puzzle.model.Word;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WordRepository extends JpaRepository<Word, Long> {
    boolean existsByWordIgnoreCase(String word);
}
