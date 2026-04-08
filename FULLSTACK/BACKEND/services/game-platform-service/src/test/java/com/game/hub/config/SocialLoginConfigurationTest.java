package com.game.hub.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialLoginConfigurationTest {

    @Test
    void shouldTreatDisabledPlaceholderAsNotConfigured() {
        SocialLoginConfiguration config = new SocialLoginConfiguration(
            "__disabled__",
            "__disabled__",
            "__disabled__",
            "__disabled__"
        );

        assertFalse(config.isGoogleEnabled());
        assertFalse(config.isFacebookEnabled());
        assertFalse(config.isAnyProviderEnabled());
    }

    @Test
    void shouldRequireBothIdAndSecretForProvider() {
        SocialLoginConfiguration partial = new SocialLoginConfiguration(
            "google-client",
            "__disabled__",
            "",
            "facebook-secret"
        );
        SocialLoginConfiguration full = new SocialLoginConfiguration(
            "google-client",
            "google-secret",
            "facebook-client",
            "facebook-secret"
        );

        assertFalse(partial.isGoogleEnabled());
        assertFalse(partial.isFacebookEnabled());
        assertTrue(full.isGoogleEnabled());
        assertTrue(full.isFacebookEnabled());
        assertTrue(full.isAnyProviderEnabled());
    }
}
