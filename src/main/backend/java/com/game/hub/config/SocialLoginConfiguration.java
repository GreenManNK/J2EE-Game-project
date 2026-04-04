package com.game.hub.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class SocialLoginConfiguration {
    private static final String DISABLED_PLACEHOLDER = "__disabled__";

    private final String googleClientId;
    private final String googleClientSecret;
    private final String facebookClientId;
    private final String facebookClientSecret;

    public SocialLoginConfiguration(
        @Value("${spring.security.oauth2.client.registration.google.client-id:" + DISABLED_PLACEHOLDER + "}") String googleClientId,
        @Value("${spring.security.oauth2.client.registration.google.client-secret:" + DISABLED_PLACEHOLDER + "}") String googleClientSecret,
        @Value("${spring.security.oauth2.client.registration.facebook.client-id:" + DISABLED_PLACEHOLDER + "}") String facebookClientId,
        @Value("${spring.security.oauth2.client.registration.facebook.client-secret:" + DISABLED_PLACEHOLDER + "}") String facebookClientSecret
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
        String normalized = trimToNull(value);
        return normalized != null && !DISABLED_PLACEHOLDER.equals(normalized);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
