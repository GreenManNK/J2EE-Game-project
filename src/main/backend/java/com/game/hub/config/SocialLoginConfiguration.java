package com.game.hub.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class SocialLoginConfiguration {
    private final String googleClientId;
    private final String googleClientSecret;
    private final String facebookClientId;
    private final String facebookClientSecret;

    public SocialLoginConfiguration(
        @Value("${SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID:}") String googleClientId,
        @Value("${SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET:}") String googleClientSecret,
        @Value("${SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_FACEBOOK_CLIENT_ID:}") String facebookClientId,
        @Value("${SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_FACEBOOK_CLIENT_SECRET:}") String facebookClientSecret
    ) {
        this.googleClientId = googleClientId;
        this.googleClientSecret = googleClientSecret;
        this.facebookClientId = facebookClientId;
        this.facebookClientSecret = facebookClientSecret;
    }

    public boolean isAnyProviderEnabled() {
        return isGoogleEnabled() || isFacebookEnabled();
    }

    public boolean isGoogleEnabled() {
        return hasText(googleClientId) && hasText(googleClientSecret);
    }

    public boolean isFacebookEnabled() {
        return hasText(facebookClientId) && hasText(facebookClientSecret);
    }

    public boolean isProviderEnabled(String provider) {
        String normalized = trimToNull(provider);
        if (normalized == null) {
            return false;
        }
        String lowered = normalized.toLowerCase(Locale.ROOT);
        if ("google".equals(lowered)) {
            return isGoogleEnabled();
        }
        if ("facebook".equals(lowered) || "fb".equals(lowered)) {
            return isFacebookEnabled();
        }
        return false;
    }

    private boolean hasText(String value) {
        return trimToNull(value) != null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
