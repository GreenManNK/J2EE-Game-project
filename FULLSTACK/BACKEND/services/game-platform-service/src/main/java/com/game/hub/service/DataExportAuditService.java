package com.game.hub.service;

import com.game.hub.config.RoleGuardInterceptor;
import com.game.hub.entity.DataExportAuditLog;
import com.game.hub.repository.DataExportAuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class DataExportAuditService {
    private final DataExportAuditLogRepository dataExportAuditLogRepository;

    public DataExportAuditService(DataExportAuditLogRepository dataExportAuditLogRepository) {
        this.dataExportAuditLogRepository = dataExportAuditLogRepository;
    }

    public void recordExport(HttpServletRequest request,
                             String reportCode,
                             String reportLabel,
                             String fileFormat,
                             String filename,
                             String scope,
                             int rowCount,
                             String targetUserId) {
        try {
            DataExportAuditLog log = new DataExportAuditLog();
            log.setExportedAt(LocalDateTime.now());
            log.setReportCode(safe(reportCode, 80));
            log.setReportLabel(safe(reportLabel, 160));
            log.setFileFormat(safe(fileFormat, 24));
            log.setFilename(safe(filename, 255));
            log.setScope(safe(scope, 40));
            log.setRowCount(Math.max(rowCount, 0));
            log.setTargetUserId(safe(trimToNull(targetUserId), 120));
            log.setActorUserId(safe(sessionValue(request, RoleGuardInterceptor.AUTH_USER_ID), 120));
            log.setActorUserRole(safe(sessionValue(request, RoleGuardInterceptor.AUTH_ROLE), 40));
            log.setClientIp(safe(resolveClientIp(request), 120));
            log.setRequestPath(safe(normalizePath(request), 300));
            log.setQueryString(safe(trimToNull(request == null ? null : request.getQueryString()), 1000));
            dataExportAuditLogRepository.save(log);
        } catch (Exception ignored) {
            // Audit logging must never break a successful export.
        }
    }

    private String sessionValue(HttpServletRequest request, String attribute) {
        if (request == null) {
            return null;
        }
        HttpSession session = request.getSession(false);
        return session == null ? null : trimToNull(session.getAttribute(attribute));
    }

    private String normalizePath(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String requestUri = trimToNull(request.getRequestURI());
        if (requestUri == null) {
            return "/";
        }
        String contextPath = trimToNull(request.getContextPath());
        if (contextPath != null && requestUri.startsWith(contextPath)) {
            String stripped = requestUri.substring(contextPath.length());
            return stripped.isBlank() ? "/" : stripped;
        }
        return requestUri;
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwardedFor = trimToNull(request.getHeader("X-Forwarded-For"));
        if (forwardedFor != null) {
            int commaIndex = forwardedFor.indexOf(',');
            if (commaIndex > 0) {
                forwardedFor = forwardedFor.substring(0, commaIndex).trim();
            }
            if (!forwardedFor.isBlank()) {
                return forwardedFor;
            }
        }

        String realIp = trimToNull(request.getHeader("X-Real-IP"));
        if (realIp != null) {
            return realIp;
        }

        return trimToNull(request.getRemoteAddr());
    }

    private String safe(String value, int maxLength) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }

    private String trimToNull(Object value) {
        if (value == null) {
            return null;
        }
        String raw = String.valueOf(value).trim();
        return raw.isEmpty() ? null : raw;
    }
}
