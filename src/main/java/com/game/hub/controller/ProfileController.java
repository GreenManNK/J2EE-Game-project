package com.game.hub.controller;

import com.game.hub.entity.UserAccount;
import com.game.hub.repository.UserAccountRepository;
import com.game.hub.service.ProfileStatsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;
import java.util.Optional;

@Controller
public class ProfileController {

    private final UserAccountRepository userAccountRepository;
    private final ProfileStatsService profileStatsService;

    public ProfileController(UserAccountRepository userAccountRepository, ProfileStatsService profileStatsService) {
        this.userAccountRepository = userAccountRepository;
        this.profileStatsService = profileStatsService;
    }

    @GetMapping("/profile/{userId}")
    public String page(@PathVariable String userId, Model model, HttpServletRequest request) {
        String viewerId = Optional.ofNullable(request.getSession(false))
            .map(s -> (String) s.getAttribute("AUTH_USER_ID"))
            .orElse("");

        model.addAllAttributes(profileStatsService.buildProfileStats(userId, viewerId));
        return "profile/index";
    }

    @GetMapping("/api/profile/{userId}")
    @ResponseBody
    public Map<String, Object> indexApi(@PathVariable String userId, HttpServletRequest request) {
        String viewerId = Optional.ofNullable(request.getSession(false))
            .map(s -> (String) s.getAttribute("AUTH_USER_ID"))
            .orElse("");

        return profileStatsService.buildProfileStats(userId, viewerId);
    }
}
