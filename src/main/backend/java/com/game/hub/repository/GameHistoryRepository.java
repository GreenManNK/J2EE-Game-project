package com.game.hub.repository;

import com.game.hub.entity.GameHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GameHistoryRepository extends JpaRepository<GameHistory, Long> {
    List<GameHistory> findByPlayer1IdOrPlayer2IdOrderByPlayedAtDesc(String player1Id, String player2Id);
    boolean existsByGameCodeAndMatchCodeAndPlayer1Id(String gameCode, String matchCode, String player1Id);
}
