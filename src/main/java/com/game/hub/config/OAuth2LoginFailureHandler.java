package com.game.hub.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
        response.sendRedirect(loginPageUrl(request) + "?socialError=" + URLEncoder.encode(message, StandardCharsets.UTF_8));
    }

    private String loginPageUrl(HttpServletRequest request) {
        String contextPath = request == null ? null : request.getContextPath();
        if (contextPath == null || contextPath.isBlank() || "/".equals(contextPath)) {
            return "/account/login-page";
        }
        return contextPath + "/account/login-page";
    }
}
