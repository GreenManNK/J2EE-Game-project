package com.game.hub.controller;

import com.game.hub.entity.UserAccount;
import com.game.hub.repository.UserAccountRepository;
import com.game.hub.service.ProfileStatsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/profile")
public class ProfileController {
    private final UserAccountRepository userAccountRepository;
    private final ProfileStatsService profileStatsService;

    public ProfileController(UserAccountRepository userAccountRepository, ProfileStatsService profileStatsService) {
        this.userAccountRepository = userAccountRepository;
        this.profileStatsService = profileStatsService;
    }

    @GetMapping
    public String page(@RequestParam String userId,
                       @RequestParam(required = false) String viewerId,
                       HttpServletRequest request,
                       Model model) {
        if (userId == null || userId.isBlank() || userAccountRepository.findById(userId).isEmpty()) {
            return "redirect:/";
        }
        String sessionUserId = sessionUserId(request);
        Map<String, Object> profile = new HashMap<>(profileStatsService.buildProfileStats(userId, safeViewerId(sessionUserId)));
        profile.put("isOwner", isOwner(userId, sessionUserId));
        model.addAllAttributes(profile);
        return "profile/index";
    }

    @ResponseBody
    @GetMapping("/api")
    public Map<String, Object> indexApi(@RequestParam String userId,
                                        @RequestParam(required = false) String viewerId,
                                        HttpServletRequest request) {
        return indexApiInternal(userId, viewerId, request);
    }

    public Map<String, Object> indexApi(String userId, String viewerId) {
        return indexApiInternal(userId, viewerId, null);
    }

    private Map<String, Object> indexApiInternal(String userId, String viewerId, HttpServletRequest request) {
        if (userId == null || userId.isBlank() || userAccountRepository.findById(userId).isEmpty()) {
            return Map.of("success", false, "error", "User not found");
        }
        String sessionUserId = sessionUserId(request);
        Map<String, Object> profile = new HashMap<>(profileStatsService.buildProfileStats(userId, safeViewerId(sessionUserId)));
        profile.put("isOwner", isOwner(userId, sessionUserId));
        return profile;
    }

    @ResponseBody
    @PatchMapping
    public Object edit(@RequestBody UpdateProfileRequest requestBody, HttpServletRequest request) {
        String sessionUserId = sessionUserId(request);
        if (sessionUserId == null) {
            return Map.of("success", false, "error", "Login required");
        }
        if (requestBody == null || requestBody.userId() == null || requestBody.userId().isBlank()) {
            return Map.of("success", false, "error", "UserId is required");
        }
        if (!sessionUserId.equals(requestBody.userId())) {
            return Map.of("success", false, "error", "User mismatch");
        }
        UserAccount user = userAccountRepository.findById(requestBody.userId()).orElse(null);
        if (user == null) {
            return Map.of("success", false, "error", "User not found");
        }

        if (requestBody.displayName() != null && !requestBody.displayName().isBlank()) {
            user.setDisplayName(requestBody.displayName());
        }

        if (requestBody.email() != null && !requestBody.email().isBlank()) {
            String newEmail = requestBody.email().trim();
            UserAccount sameEmailUser = userAccountRepository.findByEmail(newEmail).orElse(null);
            if (sameEmailUser != null && !sameEmailUser.getId().equals(user.getId())) {
                return Map.of("success", false, "error", "Email already exists");
            }
            user.setEmail(newEmail);
            user.setUsername(newEmail);
        }

        if (requestBody.avatarPath() != null && !requestBody.avatarPath().isBlank()) {
            user.setAvatarPath(requestBody.avatarPath());
        }

        userAccountRepository.save(user);
        return Map.of("success", true, "user", user);
    }

    private String safeViewerId(String sessionUserId) {
        return sessionUserId == null ? "" : sessionUserId;
    }

    private boolean isOwner(String profileUserId, String sessionUserId) {
        return profileUserId != null && profileUserId.equals(sessionUserId);
    }

    private String sessionUserId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute("AUTH_USER_ID");
        if (value == null) {
            return null;
        }
        String userId = String.valueOf(value).trim();
        return userId.isEmpty() ? null : userId;
    }

    public record UpdateProfileRequest(String userId, String displayName, String email, String avatarPath) {
    }
}
