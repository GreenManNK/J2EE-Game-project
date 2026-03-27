package com.game.hub.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String message = "Social login failed. Please try again.";
        HttpSession session = request == null ? null : request.getSession(false);
        boolean socialLinkMode = false;
        if (session != null) {
            Object linkUserId = session.getAttribute(OAuth2LoginSuccessHandler.SOCIAL_LINK_USER_ID);
            socialLinkMode = linkUserId != null && String.valueOf(linkUserId).trim().length() > 0;
            session.removeAttribute(OAuth2LoginSuccessHandler.SOCIAL_LINK_USER_ID);
            session.removeAttribute(OAuth2LoginSuccessHandler.SOCIAL_LINK_PROVIDER);
        }

        String encoded = URLEncoder.encode(message, StandardCharsets.UTF_8);
        if (socialLinkMode) {
            response.sendRedirect(settingsPageUrl(request) + "?socialError=" + encoded);
            return;
        }
        response.sendRedirect(loginPageUrl(request) + "?socialError=" + encoded);
    }

    private String loginPageUrl(HttpServletRequest request) {
        String contextPath = request == null ? null : request.getContextPath();
        if (contextPath == null || contextPath.isBlank() || "/".equals(contextPath)) {
            return "/account/login-page";
        }
        return contextPath + "/account/login-page";
    }

    private String settingsPageUrl(HttpServletRequest request) {
        String contextPath = request == null ? null : request.getContextPath();
        if (contextPath == null || contextPath.isBlank() || "/".equals(contextPath)) {
            return "/settings";
        }
        return contextPath + "/settings";
    }
}
