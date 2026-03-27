package com.game.hub.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
public class RoleGuardInterceptor implements HandlerInterceptor {
    public static final String AUTH_USER_ID = "AUTH_USER_ID";
    public static final String AUTH_ROLE = "AUTH_ROLE";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Set<String> requiredRoles = requiredRoles(request.getRequestURI(), request.getContextPath());
        if (requiredRoles.isEmpty()) {
            return true;
        }

        HttpSession session = request.getSession(false);
        String role = session == null ? null : asString(session.getAttribute(AUTH_ROLE));
        if (role == null || role.isBlank()) {
            return rejectUnauthorized(request, response);
        }

        if (requiredRoles.stream().noneMatch(r -> r.equalsIgnoreCase(role))) {
            return rejectForbidden(request, response);
        }

        return true;
    }

    private Set<String> requiredRoles(String requestUri, String contextPath) {
        String path = requestUri == null ? "" : requestUri;
        if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }

        if (path.startsWith("/admin")) {
            return Set.of("Admin");
        }
        if (path.startsWith("/notification-admin")) {
            return Set.of("Admin");
        }
        if (path.startsWith("/account/users")) {
            return Set.of("Admin");
        }
        if (path.startsWith("/history/export-csv") || path.startsWith("/history/export-excel")) {
            return Set.of("Admin");
        }
        if (path.startsWith("/leaderboard/export-csv") || path.startsWith("/leaderboard/export-excel")) {
            return Set.of("Admin");
        }
        if (path.startsWith("/manager/export-users-csv") || path.startsWith("/manager/export-users-excel")) {
            return Set.of("Admin");
        }
        if (path.startsWith("/manager")) {
            return Set.of("Manager", "Admin");
        }
        return Set.of();
    }

    private boolean rejectUnauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (wantsJson(request)) {
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "Login required");
        } else {
            response.sendRedirect(loginPageUrl(request));
        }
        return false;
    }

    private boolean rejectForbidden(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (wantsJson(request)) {
            writeJson(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden");
        } else {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden");
        }
        return false;
    }

    private boolean wantsJson(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        String accept = request.getHeader("Accept");
        String requestedWith = request.getHeader("X-Requested-With");
        String contentType = request.getContentType();
        boolean nonPageMethod = method != null
            && !("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method));
        return (path != null && path.contains("/api"))
            || (accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE))
            || (contentType != null && contentType.contains(MediaType.APPLICATION_JSON_VALUE))
            || "XMLHttpRequest".equalsIgnoreCase(requestedWith)
            || nonPageMethod;
    }

    private void writeJson(HttpServletResponse response, int status, String error) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"success\":false,\"error\":\"" + error + "\"}");
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String loginPageUrl(HttpServletRequest request) {
        String contextPath = request == null ? null : request.getContextPath();
        if (contextPath == null || contextPath.isBlank() || "/".equals(contextPath)) {
            return "/account/login-page";
        }
        return contextPath + "/account/login-page";
    }
}
