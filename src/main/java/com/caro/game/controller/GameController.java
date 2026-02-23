package com.caro.game.controller;

import com.caro.game.entity.UserAccount;
import com.caro.game.repository.UserAccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
@RequestMapping("/game")
public class GameController {
    private final UserAccountRepository userAccountRepository;

    public GameController(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @GetMapping
    public String index(@RequestParam(required = false) String roomId,
                        @RequestParam(required = false) String symbol,
                        HttpServletRequest request,
                        Model model) {
        if (sessionUser(request) == null) {
            return "redirect:/account/login-page";
        }
        model.addAttribute("roomId", roomId == null ? "" : roomId);
        model.addAttribute("symbol", symbol == null ? "" : symbol);
        addSessionPlayer(model, request);
        return "game/index";
    }

    @ResponseBody
    @GetMapping("/api")
    public Map<String, Object> indexApi(@RequestParam(required = false) String roomId,
                                        @RequestParam(required = false) String symbol,
                                        HttpServletRequest request) {
        UserAccount user = sessionUser(request);
        return Map.of(
            "roomId", roomId == null ? "" : roomId,
            "symbol", symbol == null ? "" : symbol,
            "sessionUserId", user == null ? "" : user.getId(),
            "sessionDisplayName", user == null ? "" : displayNameOf(user),
            "sessionAvatarPath", user == null ? "" : avatarPathOf(user)
        );
    }

    @GetMapping("/offline")
    public String offline() {
        return "game/offline";
    }

    @GetMapping("/waiting")
    public String waiting(@RequestParam String requestId, Model model) {
        model.addAttribute("requestId", requestId);
        return "game/waiting";
    }

    private void addSessionPlayer(Model model, HttpServletRequest request) {
        UserAccount user = sessionUser(request);
        model.addAttribute("sessionUserId", user == null ? "" : user.getId());
        model.addAttribute("sessionDisplayName", user == null ? "" : displayNameOf(user));
        model.addAttribute("sessionAvatarPath", user == null ? "" : avatarPathOf(user));
    }

    private UserAccount sessionUser(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object authUserId = session.getAttribute("AUTH_USER_ID");
        if (authUserId == null) {
            return null;
        }
        String userId = String.valueOf(authUserId).trim();
        if (userId.isEmpty()) {
            return null;
        }
        return userAccountRepository.findById(userId).orElse(null);
    }

    private String displayNameOf(UserAccount user) {
        String displayName = user.getDisplayName();
        return (displayName == null || displayName.isBlank()) ? user.getId() : displayName;
    }

    private String avatarPathOf(UserAccount user) {
        return user.getAvatarPath() == null ? "" : user.getAvatarPath();
    }
}
