package com.game.hub.games.puzzle.repository;

import com.game.hub.games.puzzle.model.JigsawPuzzle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JigsawPuzzleRepository extends JpaRepository<JigsawPuzzle, Long> {
}
