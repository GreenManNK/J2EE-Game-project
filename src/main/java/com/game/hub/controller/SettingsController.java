package com.game.hub.controller;

import com.game.hub.entity.UserAccount;
import com.game.hub.repository.UserAccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/settings")
public class SettingsController {
    private final UserAccountRepository userAccountRepository;

    public SettingsController(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @GetMapping
    public String index(Model model, HttpServletRequest request) {
        HttpSession session = request == null ? null : request.getSession(false);
        String authUserId = session == null ? null : toTrimmed(session.getAttribute("AUTH_USER_ID"));
        UserAccount settingsUser = authUserId == null ? null : userAccountRepository.findById(authUserId).orElse(null);

        model.addAttribute("authUserId", authUserId == null ? "" : authUserId);
        model.addAttribute("isAuthenticated", settingsUser != null);
        model.addAttribute("settingsUser", settingsUser);
        return "settings/index";
    }

    private String toTrimmed(Object value) {
        if (value == null) {
            return null;
        }
        String raw = String.valueOf(value).trim();
        return raw.isEmpty() ? null : raw;
    }
}
