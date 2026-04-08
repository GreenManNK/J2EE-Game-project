package com.game.hub.config;

import com.game.hub.entity.UserAccount;
import com.game.hub.repository.UserAccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.ui.Model;

import java.util.Locale;

@ControllerAdvice(annotations = Controller.class)
public class GlobalUiModelAttributes {
    private final UserAccountRepository userAccountRepository;

    public GlobalUiModelAttributes(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @ModelAttribute
    public void populate(Model model, HttpServletRequest request) {
        LanguageSelection selection = resolveLanguage(request);
        model.addAttribute("appLanguage", selection.language());
        model.addAttribute("appLanguagePinned", selection.pinned());
    }

    private LanguageSelection resolveLanguage(HttpServletRequest request) {
        String requestedLanguage = normalizeLanguage(request == null ? null : request.getParameter("lang"));
        if (requestedLanguage != null) {
            return new LanguageSelection(requestedLanguage, true);
        }

        HttpSession session = request == null ? null : request.getSession(false);
        String userId = session == null ? null : trimToNull(session.getAttribute(RoleGuardInterceptor.AUTH_USER_ID));
        if (userId != null) {
            UserAccount user = userAccountRepository.findById(userId).orElse(null);
            String userLanguage = normalizeLanguage(user == null ? null : user.getLanguage());
            if (userLanguage != null) {
                return new LanguageSelection(userLanguage, true);
            }
        }

        String acceptLanguage = request == null ? null : trimToNull(request.getHeader("Accept-Language"));
        if (acceptLanguage != null && acceptLanguage.toLowerCase(Locale.ROOT).startsWith("en")) {
            return new LanguageSelection("en", false);
        }

        return new LanguageSelection("vi", false);
    }

    private String normalizeLanguage(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        String lowered = normalized.toLowerCase(Locale.ROOT);
        if (lowered.startsWith("en")) {
            return "en";
        }
        if (lowered.startsWith("vi")) {
            return "vi";
        }
        return null;
    }

    private String trimToNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private record LanguageSelection(String language, boolean pinned) {
    }
}
