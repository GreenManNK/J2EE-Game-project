package com.game.hub.games.typing.repository;

import com.game.hub.games.typing.model.TypingText;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface TypingTextRepository extends JpaRepository<TypingText, Long> {

    @Query(value = "SELECT * FROM typing_text ORDER BY RAND() LIMIT 1", nativeQuery = true)
    TypingText findRandomText();
}
