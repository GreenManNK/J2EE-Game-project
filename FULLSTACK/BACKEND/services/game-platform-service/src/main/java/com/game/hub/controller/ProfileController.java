package com.game.hub.controller;

import com.game.hub.service.ProfileStatsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;
import java.util.Optional;

@Controller
public class ProfileController {
    private final ProfileStatsService profileStatsService;

    public ProfileController(ProfileStatsService profileStatsService) {
        this.profileStatsService = profileStatsService;
    }

    @GetMapping("/profile")
    public String profileHome(@RequestParam(required = false) String userId,
                              Model model,
                              HttpServletRequest request) {
        String targetUserId = resolveTargetUserId(userId, request);
        if (targetUserId == null || targetUserId.isBlank()) {
            return "redirect:/account/login-page";
        }
        return renderProfile(targetUserId, model, request);
    }

    @GetMapping("/profile/{userId}")
    public String page(@PathVariable String userId, Model model, HttpServletRequest request) {
        String targetUserId = resolveTargetUserId(userId, request);
        if (targetUserId == null || targetUserId.isBlank()) {
            return "redirect:/account/login-page";
        }
        return renderProfile(targetUserId, model, request);
    }

    @GetMapping("/api/profile")
    @ResponseBody
    public Map<String, Object> indexApiSelf(@RequestParam(required = false) String userId, HttpServletRequest request) {
        String targetUserId = resolveTargetUserId(userId, request);
        if (targetUserId == null || targetUserId.isBlank()) {
            return Map.of("success", false, "error", "Login required");
        }
        String viewerId = Optional.ofNullable(resolveAuthenticatedUserId(request)).orElse("");
        return profileStatsService.buildProfileStats(targetUserId, viewerId);
    }

    @GetMapping("/api/profile/{userId}")
    @ResponseBody
    public Map<String, Object> indexApi(@PathVariable String userId, HttpServletRequest request) {
        String targetUserId = resolveTargetUserId(userId, request);
        if (targetUserId == null || targetUserId.isBlank()) {
            return Map.of("success", false, "error", "Login required");
        }
        String viewerId = Optional.ofNullable(resolveAuthenticatedUserId(request)).orElse("");
        return profileStatsService.buildProfileStats(targetUserId, viewerId);
    }

    private String toTrimmed(Object value) {
        if (value == null) {
            return null;
        }
        String raw = String.valueOf(value).trim();
        return raw.isEmpty() ? null : raw;
    }

    private String renderProfile(String userId, Model model, HttpServletRequest request) {
        String viewerId = Optional.ofNullable(resolveAuthenticatedUserId(request)).orElse("");
        model.addAllAttributes(profileStatsService.buildProfileStats(userId, viewerId));
        return "profile/index";
    }

    private String resolveTargetUserId(String requestedUserId, HttpServletRequest request) {
        String normalizedRequested = toTrimmed(requestedUserId);
        String sessionUserId = resolveAuthenticatedUserId(request);
        HttpSession session = request == null ? null : request.getSession(false);
        String sessionRole = session == null ? null : toTrimmed(session.getAttribute("AUTH_ROLE"));

        if (normalizedRequested == null) {
            return sessionUserId;
        }
        if (sessionUserId == null) {
            return null;
        }
        if (sessionUserId.equals(normalizedRequested)) {
            return normalizedRequested;
        }
        if (sessionRole != null
            && ("admin".equalsIgnoreCase(sessionRole) || "manager".equalsIgnoreCase(sessionRole))) {
            return normalizedRequested;
        }
        return sessionUserId;
    }

    private String resolveAuthenticatedUserId(HttpServletRequest request) {
        HttpSession session = request == null ? null : request.getSession(false);
        String sessionUserId = session == null ? null : toTrimmed(session.getAttribute("AUTH_USER_ID"));
        if (sessionUserId != null) {
            return sessionUserId;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        String authUserId = toTrimmed(authentication.getName());
        if (authUserId == null || "anonymousUser".equalsIgnoreCase(authUserId)) {
            return null;
        }

        if (request != null) {
            HttpSession activeSession = request.getSession(true);
            activeSession.setAttribute("AUTH_USER_ID", authUserId);
            String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(value -> value != null && value.startsWith("ROLE_"))
                .map(value -> value.substring("ROLE_".length()))
                .findFirst()
                .orElse(null);
            if (role != null) {
                activeSession.setAttribute("AUTH_ROLE", role);
            }
        }
        return authUserId;
    }
}
