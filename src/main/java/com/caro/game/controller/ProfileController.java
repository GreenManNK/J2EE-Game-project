package com.caro.game.controller;

import com.caro.game.entity.UserAccount;
import com.caro.game.repository.UserAccountRepository;
import com.caro.game.service.ProfileStatsService;
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
        String current = resolveViewerId(userId, viewerId, request);
        model.addAllAttributes(profileStatsService.buildProfileStats(userId, current));
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
        String current = resolveViewerId(userId, viewerId, request);
        return profileStatsService.buildProfileStats(userId, current);
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

    private String resolveViewerId(String userId, String viewerId, HttpServletRequest request) {
        String sessionUserId = sessionUserId(request);
        if (sessionUserId != null) {
            return sessionUserId;
        }
        // Prevent spoofing owner UI when no authenticated session exists.
        return "";
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
