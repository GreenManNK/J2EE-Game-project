package com.caro.game.repository;

import com.caro.game.entity.ChallengeRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChallengeRequestRepository extends JpaRepository<ChallengeRequest, Long> {
}