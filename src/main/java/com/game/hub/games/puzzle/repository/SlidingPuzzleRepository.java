package com.game.hub.games.puzzle.repository;

import com.game.hub.games.puzzle.model.SlidingPuzzle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SlidingPuzzleRepository extends JpaRepository<SlidingPuzzle, Long> {
}
