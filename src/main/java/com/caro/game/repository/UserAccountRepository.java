package com.caro.game.repository;

import com.caro.game.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, String> {
    Optional<UserAccount> findByEmail(String email);
    Optional<UserAccount> findByUsername(String username);
    List<UserAccount> findAllByOrderByScoreDesc();
}
