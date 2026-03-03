package com.game.hub.repository;

import com.game.hub.entity.PageAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PageAccessLogRepository extends JpaRepository<PageAccessLog, Long> {
    List<PageAccessLog> findAllByOrderByVisitedAtDesc();
}
