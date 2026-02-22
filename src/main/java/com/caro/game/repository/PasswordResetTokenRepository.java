package com.caro.game.repository;

import com.caro.game.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findTopByUserIdAndTokenOrderByCreatedAtDesc(String userId, String token);
    List<PasswordResetToken> findByUserId(String userId);
}