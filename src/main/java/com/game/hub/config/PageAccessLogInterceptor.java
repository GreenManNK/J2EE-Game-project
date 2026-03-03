package com.game.hub.config;

import com.game.hub.entity.PageAccessLog;
import com.game.hub.repository.PageAccessLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;

@Component
public class PageAccessLogInterceptor implements HandlerInterceptor {
    private static final String AUTH_USER_ID = "AUTH_USER_ID";
    private static final String AUTH_ROLE = "AUTH_ROLE";

    private final PageAccessLogRepository pageAccessLogRepository;

    public PageAccessLogInterceptor(PageAccessLogRepository pageAccessLogRepository) {
        this.pageAccessLogRepository = pageAccessLogRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (request == null) {
            return true;
        }
        String method = safe(request.getMethod(), 12);
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        try {
            HttpSession session = request.getSession(false);
            String sessionUserId = session == null ? null : asString(session.getAttribute(AUTH_USER_ID));
            String sessionRole = session == null ? null : asString(session.getAttribute(AUTH_ROLE));

            PageAccessLog log = new PageAccessLog();
            log.setVisitedAt(LocalDateTime.now());
            log.setHttpMethod(method);
            log.setRequestPath(safe(normalizePath(request), 300));
            log.setQueryString(safe(request.getQueryString(), 1000));
            log.setUserId(safe(sessionUserId, 120));
            log.setUserRole(safe(sessionRole, 40));
            log.setClientIp(safe(resolveClientIp(request), 120));
            log.setUserAgent(safe(request.getHeader("User-Agent"), 1000));
            log.setReferer(safe(request.getHeader("Referer"), 1000));
            pageAccessLogRepository.save(log);
        } catch (Exception ignored) {
            // Access logging must not block business requests.
        }
        return true;
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String raw = String.valueOf(value).trim();
        return raw.isEmpty() ? null : raw;
    }

    private String safe(String value, int maxLength) {
        String normalized = asString(value);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }

    private String normalizePath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        if (requestUri == null || requestUri.isBlank()) {
            return "/";
        }
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && requestUri.startsWith(contextPath)) {
            String stripped = requestUri.substring(contextPath.length());
            return stripped.isBlank() ? "/" : stripped;
        }
        return requestUri;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = asString(request.getHeader("X-Forwarded-For"));
        if (forwardedFor != null) {
            int commaIndex = forwardedFor.indexOf(',');
            if (commaIndex > 0) {
                forwardedFor = forwardedFor.substring(0, commaIndex).trim();
            }
            if (!forwardedFor.isBlank()) {
                return forwardedFor;
            }
        }

        String realIp = asString(request.getHeader("X-Real-IP"));
        if (realIp != null) {
            return realIp;
        }

        return asString(request.getRemoteAddr());
    }
}
