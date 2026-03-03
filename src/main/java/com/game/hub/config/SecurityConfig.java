package com.game.hub.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
public class SecurityConfig {
    private final SessionRoleAuthenticationFilter sessionRoleAuthenticationFilter;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final OAuth2LoginFailureHandler oAuth2LoginFailureHandler;
    private final String googleClientId;
    private final String facebookClientId;

    public SecurityConfig(SessionRoleAuthenticationFilter sessionRoleAuthenticationFilter,
                          OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler,
                          OAuth2LoginFailureHandler oAuth2LoginFailureHandler,
                          @Value("${SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID:}") String googleClientId,
                          @Value("${SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_FACEBOOK_CLIENT_ID:}") String facebookClientId) {
        this.sessionRoleAuthenticationFilter = sessionRoleAuthenticationFilter;
        this.oAuth2LoginSuccessHandler = oAuth2LoginSuccessHandler;
        this.oAuth2LoginFailureHandler = oAuth2LoginFailureHandler;
        this.googleClientId = googleClientId;
        this.facebookClientId = facebookClientId;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringRequestMatchers("/ws/**"))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .addFilterBefore(sessionRoleAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint())
                .accessDeniedHandler(accessDeniedHandler()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/admin/**", "/notification-admin/**", "/account/users").hasRole("ADMIN")
                .requestMatchers("/manager/**").hasAnyRole("MANAGER", "ADMIN")
                .anyRequest().permitAll());

        if (isSocialLoginConfigured()) {
            http.oauth2Login(oauth2 -> oauth2
                .loginPage("/account/login-page")
                .successHandler(oAuth2LoginSuccessHandler)
                .failureHandler(oAuth2LoginFailureHandler));
        }

        http
            .httpBasic(httpBasic -> httpBasic.disable())
            .formLogin(form -> form.disable())
            .logout(logout -> logout.disable());
        return http.build();
    }

    private boolean isSocialLoginConfigured() {
        return hasText(googleClientId) || hasText(facebookClientId);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            if (wantsJson(request)) {
                writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "Login required");
            } else {
                response.sendRedirect(loginPageUrl(request));
            }
        };
    }

    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            if (wantsJson(request)) {
                writeJson(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden");
            } else {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden");
            }
        };
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
            || (accept != null && accept.contains("application/json"))
            || (contentType != null && contentType.contains("application/json"))
            || "XMLHttpRequest".equalsIgnoreCase(requestedWith)
            || nonPageMethod;
    }

    private String loginPageUrl(HttpServletRequest request) {
        String contextPath = request == null ? null : request.getContextPath();
        if (contextPath == null || contextPath.isBlank() || "/".equals(contextPath)) {
            return "/account/login-page";
        }
        return contextPath + "/account/login-page";
    }

    private void writeJson(HttpServletResponse response, int status, String error) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.getWriter().write("{\"success\":false,\"error\":\"" + error + "\"}");
    }
}
