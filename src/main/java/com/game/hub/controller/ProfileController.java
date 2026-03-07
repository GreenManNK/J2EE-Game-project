package com.game.hub.controller;

import com.game.hub.service.AccountService;
import com.game.hub.service.AvatarStorageService;
import com.game.hub.service.ProfileStatsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Optional;

@Controller
public class ProfileController {
    private final AccountService accountService;
    private final ProfileStatsService profileStatsService;
    private final AvatarStorageService avatarStorageService;

    public ProfileController(AccountService accountService,
                             ProfileStatsService profileStatsService,
                             AvatarStorageService avatarStorageService) {
        this.accountService = accountService;
        this.profileStatsService = profileStatsService;
        this.avatarStorageService = avatarStorageService;
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

    @PatchMapping("/profile")
    @ResponseBody
    public Map<String, Object> updateProfile(@RequestBody(required = false) UpdateProfileRequest request,
                                             HttpServletRequest httpRequest) {
        HttpSession session = httpRequest == null ? null : httpRequest.getSession(false);
        String sessionUserId = session == null ? null : toTrimmed(session.getAttribute("AUTH_USER_ID"));
        if (sessionUserId == null) {
            return Map.of("success", false, "error", "Login required");
        }
        if (request == null) {
            return Map.of("success", false, "error", "Invalid request");
        }
        String requestedUserId = toTrimmed(request.userId());
        if (requestedUserId != null && !sessionUserId.equals(requestedUserId)) {
            return Map.of("success", false, "error", "User mismatch");
        }

        AccountService.ServiceResult result = accountService.updateProfile(
            sessionUserId,
            request.displayName(),
            request.email(),
            request.avatarPath()
        );
        if (!result.success()) {
            return Map.of("success", false, "error", result.error());
        }
        if (result.data() instanceof Map<?, ?> userMap) {
            return Map.of("success", true, "user", userMap);
        }
        return Map.of("success", true);
    }

    @PostMapping(value = "/profile/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public Map<String, Object> uploadAvatar(@RequestParam("avatar") MultipartFile avatar,
                                            HttpServletRequest httpRequest) {
        HttpSession session = httpRequest == null ? null : httpRequest.getSession(false);
        String sessionUserId = session == null ? null : toTrimmed(session.getAttribute("AUTH_USER_ID"));
        if (sessionUserId == null) {
            return Map.of("success", false, "error", "Login required");
        }

        AvatarStorageService.StoreResult storeResult = avatarStorageService.store(avatar);
        if (!storeResult.success()) {
            return Map.of("success", false, "error", storeResult.error());
        }

        AccountService.ServiceResult updateResult = accountService.updateAvatar(sessionUserId, storeResult.avatarPath());
        if (!updateResult.success()) {
            return Map.of("success", false, "error", updateResult.error());
        }
        if (updateResult.data() instanceof Map<?, ?> userMap) {
            return Map.of("success", true, "avatarPath", storeResult.avatarPath(), "user", userMap);
        }
        return Map.of("success", true, "avatarPath", storeResult.avatarPath());
    }

    private String toTrimmed(Object value) {
        if (value == null) {
            return null;
        }
        String raw = String.valueOf(value).trim();
        return raw.isEmpty() ? null : raw;
    }

    public record UpdateProfileRequest(String userId, String displayName, String email, String avatarPath) {
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
