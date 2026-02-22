package com.caro.game.controller;

import com.caro.game.entity.UserAccount;
import com.caro.game.repository.UserAccountRepository;
import com.caro.game.service.ProfileStatsService;
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
                       Model model) {
        String current = viewerId == null ? userId : viewerId;
        model.addAllAttributes(profileStatsService.buildProfileStats(userId, current));
        return "profile/index";
    }

    @ResponseBody
    @GetMapping("/api")
    public Map<String, Object> indexApi(@RequestParam String userId, @RequestParam(required = false) String viewerId) {
        String current = viewerId == null ? userId : viewerId;
        return profileStatsService.buildProfileStats(userId, current);
    }

    @ResponseBody
    @PatchMapping
    public Object edit(@RequestBody UpdateProfileRequest request) {
        UserAccount user = userAccountRepository.findById(request.userId()).orElse(null);
        if (user == null) {
            return Map.of("success", false, "error", "User not found");
        }

        if (request.displayName() != null && !request.displayName().isBlank()) {
            user.setDisplayName(request.displayName());
        }

        if (request.email() != null && !request.email().isBlank()) {
            user.setEmail(request.email());
            user.setUsername(request.email());
        }

        if (request.avatarPath() != null && !request.avatarPath().isBlank()) {
            user.setAvatarPath(request.avatarPath());
        }

        userAccountRepository.save(user);
        return Map.of("success", true, "user", user);
    }

    public record UpdateProfileRequest(String userId, String displayName, String email, String avatarPath) {
    }
}
