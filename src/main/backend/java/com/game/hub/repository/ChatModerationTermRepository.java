package com.game.hub.repository;

import com.game.hub.entity.ChatModerationTerm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatModerationTermRepository extends JpaRepository<ChatModerationTerm, Long> {
    boolean existsByTermIgnoreCase(String term);
    List<ChatModerationTerm> findAllByOrderByTermAsc();
}
