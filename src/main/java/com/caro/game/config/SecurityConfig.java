package com.caro.game.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
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

    public SecurityConfig(SessionRoleAuthenticationFilter sessionRoleAuthenticationFilter) {
        this.sessionRoleAuthenticationFilter = sessionRoleAuthenticationFilter;
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
                .requestMatchers("/admin/**", "/notification-admin/**").hasRole("ADMIN")
                .requestMatchers("/manager/**").hasAnyRole("MANAGER", "ADMIN")
                .anyRequest().permitAll())
            .httpBasic(httpBasic -> httpBasic.disable())
            .formLogin(form -> form.disable())
            .logout(logout -> logout.disable());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            if (wantsJson(request)) {
                writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "Login required");
            } else {
                response.sendRedirect("/account/login-page");
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
        String accept = request.getHeader("Accept");
        String requestedWith = request.getHeader("X-Requested-With");
        return (path != null && path.contains("/api"))
            || (accept != null && accept.contains("application/json"))
            || "XMLHttpRequest".equalsIgnoreCase(requestedWith);
    }

    private void writeJson(HttpServletResponse response, int status, String error) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.getWriter().write("{\"success\":false,\"error\":\"" + error + "\"}");
    }
}
