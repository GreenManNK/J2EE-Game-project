package com.game.hub.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AccountSyncApiKeyService {
    private final String configuredApiKey;

    public AccountSyncApiKeyService(@Value("${app.api.sync-key:}") String configuredApiKey) {
        this.configuredApiKey = configuredApiKey == null ? "" : configuredApiKey.trim();
    }

    public AuthCheckResult validate(String apiKeyHeader, String authorizationHeader) {
        if (configuredApiKey.isBlank()) {
            return new AuthCheckResult(false, false, "Sync API is disabled");
        }
        String provided = extractProvidedKey(apiKeyHeader, authorizationHeader);
        if (provided == null || !configuredApiKey.equals(provided)) {
            return new AuthCheckResult(false, true, "Invalid API key");
        }
        return new AuthCheckResult(true, true, null);
    }

    private String extractProvidedKey(String apiKeyHeader, String authorizationHeader) {
        String direct = trimToNull(apiKeyHeader);
        if (direct != null) {
            return direct;
        }

        String authorization = trimToNull(authorizationHeader);
        if (authorization == null) {
            return null;
        }
        if (authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return trimToNull(authorization.substring(7));
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record AuthCheckResult(boolean authorized, boolean configured, String error) {
    }
}
