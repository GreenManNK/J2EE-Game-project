package com.game.hub.repository;

import com.game.hub.entity.ChallengeRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChallengeRequestRepository extends JpaRepository<ChallengeRequest, Long> {
}