package com.game.hub.controller;

import com.game.hub.entity.UserAccount;
import com.game.hub.repository.UserAccountRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
@RequestMapping("/leaderboard")
public class LeaderboardController {
    private final UserAccountRepository userAccountRepository;

    public LeaderboardController(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @GetMapping
    public String page(Model model) {
        model.addAttribute("players", buildItems());
        return "leaderboard/index";
    }

    @ResponseBody
    @GetMapping("/api")
    public List<LeaderboardItem> indexApi() {
        return buildItems();
    }

    private List<LeaderboardItem> buildItems() {
        return userAccountRepository.findAllByOrderByScoreDesc().stream()
            .map(u -> new LeaderboardItem(u.getId(), u.getDisplayName(), u.getScore(), u.getAvatarPath()))
            .toList();
    }

    public record LeaderboardItem(String userId, String displayName, int score, String avatarPath) {
    }
}
