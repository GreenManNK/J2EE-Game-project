package com.game.hub.repository;

import com.game.hub.entity.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {
    Optional<EmailVerificationToken> findTopByEmailAndTokenOrderByCreatedAtDesc(String email, String token);
    List<EmailVerificationToken> findByEmail(String email);
}