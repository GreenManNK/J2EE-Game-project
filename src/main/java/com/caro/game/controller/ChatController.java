package com.caro.game.controller;

import com.caro.game.entity.UserAccount;
import com.caro.game.repository.UserAccountRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
@RequestMapping("/chat")
public class ChatController {
    private final UserAccountRepository userAccountRepository;

    public ChatController(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @GetMapping("/private")
    public String privatePage(@RequestParam String currentUserId,
                              @RequestParam String friendId,
                              Model model) {
        model.addAllAttributes(privateChat(currentUserId, friendId));
        return "chat/private";
    }

    @ResponseBody
    @GetMapping("/private/api")
    public Map<String, Object> privateChat(@RequestParam String currentUserId, @RequestParam String friendId) {
        UserAccount currentUser = userAccountRepository.findById(currentUserId).orElse(null);
        UserAccount friend = userAccountRepository.findById(friendId).orElse(null);

        if (currentUser == null || friend == null) {
            return Map.of("success", false, "error", "User not found");
        }

        return Map.of(
            "success", true,
            "currentUserId", currentUser.getId(),
            "currentUserName", currentUser.getDisplayName(),
            "friendId", friend.getId(),
            "friendName", friend.getDisplayName()
        );
    }
}