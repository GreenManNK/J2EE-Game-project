package com.game.hub.config;

import com.game.hub.entity.UserAccount;
import com.game.hub.repository.UserAccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class BannedUserAccessInterceptor implements HandlerInterceptor {
    private final UserAccountRepository userAccountRepository;

    public BannedUserAccessInterceptor(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = normalizedPath(request.getRequestURI(), request.getContextPath());
        if (isBypassedPath(path)) {
            return true;
        }

        HttpSession session = request.getSession(false);
        String userId = session == null ? null : trimToNull(session.getAttribute(RoleGuardInterceptor.AUTH_USER_ID));
        if (userId == null) {
            return true;
        }

        UserAccount user = userAccountRepository.findById(userId).orElse(null);
        if (user == null || !user.isBanned()) {
            return true;
        }

        String message = "Account banned until " + user.getBannedUntil();
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();

        if (wantsJson(request)) {
            writeJson(response, HttpServletResponse.SC_FORBIDDEN, message);
            return false;
        }

        String encoded = URLEncoder.encode(message, StandardCharsets.UTF_8);
        response.sendRedirect(banNotificationPageUrl(request) + "?message=" + encoded);
        return false;
    }

    private boolean isBypassedPath(String path) {
        return "/account/ban-notification".equals(path)
            || "/account/ban-notification-page".equals(path);
    }

    private String normalizedPath(String requestUri, String contextPath) {
        String path = requestUri == null ? "" : requestUri;
        if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return path;
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

    private String banNotificationPageUrl(HttpServletRequest request) {
        String contextPath = request == null ? null : request.getContextPath();
        if (contextPath == null || contextPath.isBlank() || "/".equals(contextPath)) {
            return "/account/ban-notification-page";
        }
        return contextPath + "/account/ban-notification-page";
    }

    private String trimToNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
