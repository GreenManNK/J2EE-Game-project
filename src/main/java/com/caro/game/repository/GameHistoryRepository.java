package com.caro.game.repository;

import com.caro.game.entity.GameHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GameHistoryRepository extends JpaRepository<GameHistory, Long> {
    List<GameHistory> findByPlayer1IdOrPlayer2IdOrderByPlayedAtDesc(String player1Id, String player2Id);
}