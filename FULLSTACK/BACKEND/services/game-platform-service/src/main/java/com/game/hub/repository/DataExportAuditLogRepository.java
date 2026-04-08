package com.game.hub.repository;

import com.game.hub.entity.DataExportAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DataExportAuditLogRepository extends JpaRepository<DataExportAuditLog, Long> {
    List<DataExportAuditLog> findTop20ByOrderByExportedAtDesc();
    List<DataExportAuditLog> findAllByOrderByExportedAtDesc();
}
