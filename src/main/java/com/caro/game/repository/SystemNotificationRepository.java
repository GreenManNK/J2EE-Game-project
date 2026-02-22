package com.caro.game.repository;

import com.caro.game.entity.SystemNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SystemNotificationRepository extends JpaRepository<SystemNotification, Long> {
    List<SystemNotification> findTop5ByOrderByCreatedAtDesc();
    List<SystemNotification> findAllByOrderByCreatedAtDesc();
}
