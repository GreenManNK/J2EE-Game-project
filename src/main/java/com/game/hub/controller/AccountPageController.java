package com.game.hub.controller;

import com.game.hub.config.OAuth2LoginSuccessHandler;
import com.game.hub.entity.UserAccount;
import com.game.hub.repository.UserAccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/account")
public class AccountPageController {
    private static final String SOCIAL_PROVIDER_GOOGLE = "google";
    private static final String SOCIAL_PROVIDER_FACEBOOK = "facebook";

    private final UserAccountRepository userAccountRepository;
    private final String googleClientId;
    private final String facebookClientId;

    public AccountPageController(UserAccountRepository userAccountRepository,
                                 @Value("${SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID:}") String googleClientId,
                                 @Value("${SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_FACEBOOK_CLIENT_ID:}") String facebookClientId) {
        this.userAccountRepository = userAccountRepository;
        this.googleClientId = googleClientId;
        this.facebookClientId = facebookClientId;
    }

    @GetMapping("/login-page")
    public String loginPage(@RequestParam(required = false) String socialError,
                            Model model) {
        if (socialError != null && !socialError.isBlank()) {
            model.addAttribute("socialError", socialError.trim());
        }
        model.addAttribute("googleLoginEnabled", hasText(googleClientId));
        model.addAttribute("facebookLoginEnabled", hasText(facebookClientId));
        return "account/login";
    }

    @GetMapping("/register-page")
    public String registerPage(Model model) {
        model.addAttribute("googleLoginEnabled", hasText(googleClientId));
        model.addAttribute("facebookLoginEnabled", hasText(facebookClientId));
        return "account/register";
    }

    @GetMapping("/verify-email-page")
    public String verifyEmailPage() {
        return "account/verify-email";
    }

    @GetMapping("/reset-page")
    public String resetPage() {
        return "account/reset-password";
    }

    @GetMapping("/ban-notification-page")
    public String banPage(@RequestParam(required = false) String message,
                          org.springframework.ui.Model model) {
        model.addAttribute("message", message == null ? "Tai khoan cua ban da bi khoa tam thoi." : message);
        return "account/ban-notification";
    }

    @GetMapping("/oauth2-success")
    public String oauth2SuccessPage(HttpServletRequest request, Model model) {
        HttpSession session = request == null ? null : request.getSession(false);
        String userId = session == null ? null : toTrimmed(session.getAttribute("AUTH_USER_ID"));
        if (userId == null) {
            return "redirect:/account/login-page?socialError=Login+required";
        }

        UserAccount user = userAccountRepository.findById(userId).orElse(null);
        if (user == null) {
            return "redirect:/account/login-page?socialError=User+not+found";
        }

        model.addAttribute("userId", user.getId());
        model.addAttribute("displayName", user.getDisplayName() == null ? "Player" : user.getDisplayName());
        model.addAttribute("email", user.getEmail() == null ? "" : user.getEmail());
        model.addAttribute("role", user.getRole() == null ? "User" : user.getRole());
        model.addAttribute(
            "avatarPath",
            user.getAvatarPath() == null ? "/uploads/avatars/default-avatar.jpg" : user.getAvatarPath()
        );
        return "account/oauth2-success";
    }

    @GetMapping("/social/{provider}/link")
    public String startSocialLink(@PathVariable String provider, HttpServletRequest request) {
        HttpSession session = request == null ? null : request.getSession(false);
        String userId = session == null ? null : toTrimmed(session.getAttribute("AUTH_USER_ID"));
        if (userId == null) {
            return "redirect:/account/login-page?socialError=Login+required";
        }

        String normalizedProvider = normalizeSocialProvider(provider);
        if (normalizedProvider == null) {
            return "redirect:/settings?socialError=Unsupported+social+provider";
        }
        if (!isSocialProviderConfigured(normalizedProvider)) {
            return "redirect:/settings?socialError=" + normalizedProvider + "+OAuth+is+not+configured";
        }

        HttpSession activeSession = request.getSession(true);
        activeSession.setAttribute(OAuth2LoginSuccessHandler.SOCIAL_LINK_USER_ID, userId);
        activeSession.setAttribute(OAuth2LoginSuccessHandler.SOCIAL_LINK_PROVIDER, normalizedProvider);
        return "redirect:/oauth2/authorization/" + normalizedProvider;
    }

    private String toTrimmed(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String normalizeSocialProvider(String provider) {
        String normalized = toTrimmed(provider);
        if (normalized == null) {
            return null;
        }
        String lowered = normalized.toLowerCase();
        if (SOCIAL_PROVIDER_GOOGLE.equals(lowered)) {
            return SOCIAL_PROVIDER_GOOGLE;
        }
        if (SOCIAL_PROVIDER_FACEBOOK.equals(lowered) || "fb".equals(lowered)) {
            return SOCIAL_PROVIDER_FACEBOOK;
        }
        return null;
    }

    private boolean isSocialProviderConfigured(String provider) {
        if (SOCIAL_PROVIDER_GOOGLE.equals(provider)) {
            return hasText(googleClientId);
        }
        if (SOCIAL_PROVIDER_FACEBOOK.equals(provider)) {
            return hasText(facebookClientId);
        }
        return false;
    }
}
