package com.game.hub.repository;

import com.game.hub.entity.SystemNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SystemNotificationRepository extends JpaRepository<SystemNotification, Long> {
    List<SystemNotification> findTop5ByOrderByCreatedAtDesc();
    List<SystemNotification> findAllByOrderByCreatedAtDesc();
}
