package com.game.hub.controller;

import com.game.hub.entity.UserAccount;
import com.game.hub.repository.UserAccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/settings")
public class SettingsController {
    private final UserAccountRepository userAccountRepository;
    private final String facebookClientId;
    private final String googleClientId;

    public SettingsController(UserAccountRepository userAccountRepository,
                              @Value("${SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_FACEBOOK_CLIENT_ID:}") String facebookClientId,
                              @Value("${SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID:}") String googleClientId) {
        this.userAccountRepository = userAccountRepository;
        this.facebookClientId = facebookClientId;
        this.googleClientId = googleClientId;
    }

    @GetMapping
    public String index(Model model,
                        HttpServletRequest request,
                        @RequestParam(required = false) String socialLinked,
                        @RequestParam(required = false) String socialError) {
        String authUserId = resolveAuthenticatedUserId(request);
        UserAccount settingsUser = authUserId == null ? null : userAccountRepository.findById(authUserId).orElse(null);

        model.addAttribute("authUserId", authUserId == null ? "" : authUserId);
        model.addAttribute("isAuthenticated", settingsUser != null);
        model.addAttribute("settingsUser", settingsUser);
        model.addAttribute("googleLoginEnabled", hasText(googleClientId));
        model.addAttribute("facebookLoginEnabled", hasText(facebookClientId));
        model.addAttribute("googleLinked", settingsUser != null && toTrimmed(settingsUser.getOauthGoogleId()) != null);
        model.addAttribute("facebookLinked", settingsUser != null && toTrimmed(settingsUser.getOauthFacebookId()) != null);
        model.addAttribute("socialLinked", toTrimmed(socialLinked));
        model.addAttribute("socialError", toTrimmed(socialError));
        return "settings/index";
    }

    private String toTrimmed(Object value) {
        if (value == null) {
            return null;
        }
        String raw = String.valueOf(value).trim();
        return raw.isEmpty() ? null : raw;
    }

    private String resolveAuthenticatedUserId(HttpServletRequest request) {
        HttpSession session = request == null ? null : request.getSession(false);
        String sessionUserId = session == null ? null : toTrimmed(session.getAttribute("AUTH_USER_ID"));
        if (sessionUserId != null) {
            return sessionUserId;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        String authUserId = toTrimmed(authentication.getName());
        if (authUserId == null || "anonymousUser".equalsIgnoreCase(authUserId)) {
            return null;
        }

        if (request != null) {
            HttpSession activeSession = request.getSession(true);
            activeSession.setAttribute("AUTH_USER_ID", authUserId);
            String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(value -> value != null && value.startsWith("ROLE_"))
                .map(value -> value.substring("ROLE_".length()))
                .findFirst()
                .orElse(null);
            if (role != null) {
                activeSession.setAttribute("AUTH_ROLE", role);
            }
        }

        return authUserId;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
