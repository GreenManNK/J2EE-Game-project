package com.game.hub.controller;

import com.game.hub.service.ProfileStatsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
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
        String viewerId = Optional.ofNullable(request.getSession(false))
            .map(s -> (String) s.getAttribute("AUTH_USER_ID"))
            .orElse("");
        return profileStatsService.buildProfileStats(targetUserId, viewerId);
    }

    @GetMapping("/api/profile/{userId}")
    @ResponseBody
    public Map<String, Object> indexApi(@PathVariable String userId, HttpServletRequest request) {
        String targetUserId = resolveTargetUserId(userId, request);
        if (targetUserId == null || targetUserId.isBlank()) {
            return Map.of("success", false, "error", "Login required");
        }
        String viewerId = Optional.ofNullable(request.getSession(false))
            .map(s -> (String) s.getAttribute("AUTH_USER_ID"))
            .orElse("");
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
        String viewerId = Optional.ofNullable(request.getSession(false))
            .map(s -> (String) s.getAttribute("AUTH_USER_ID"))
            .orElse("");
        model.addAllAttributes(profileStatsService.buildProfileStats(userId, viewerId));
        return "profile/index";
    }

    private String resolveTargetUserId(String requestedUserId, HttpServletRequest request) {
        String normalizedRequested = toTrimmed(requestedUserId);
        HttpSession session = request == null ? null : request.getSession(false);
        String sessionUserId = session == null ? null : toTrimmed(session.getAttribute("AUTH_USER_ID"));
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
}
