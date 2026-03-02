package com.game.hub.games.quiz.repository;

import com.game.hub.games.quiz.entity.HighScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HighScoreRepository extends JpaRepository<HighScore, Long> {
    List<HighScore> findTop10ByOrderByScoreDesc();
}
