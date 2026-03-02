package com.game.hub.games.puzzle.repository;

import com.game.hub.games.puzzle.model.Sudoku;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SudokuRepository extends JpaRepository<Sudoku, Long> {
}
