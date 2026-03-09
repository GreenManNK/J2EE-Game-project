package com.game.hub.config;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

class WebSocketAllowedOriginResolverTest {

    @Test
    void resolvesConfiguredAndDerivedOriginsWithoutDuplicates() {
        WebSocketAllowedOriginResolver resolver = new WebSocketAllowedOriginResolver(
            "http://localhost:*,https://*.trycloudflare.com",
            "https://game.example.com",
            "https://game.example.com/Game",
            "https://vpn.example.net:8443,https://game.example.com"
        );

        List<String> origins = Arrays.asList(resolver.resolve());

        assertIterableEquals(
            List.of(
                "http://localhost:*",
                "https://*.trycloudflare.com",
                "https://game.example.com",
                "https://vpn.example.net:8443"
            ),
            origins
        );
    }

    @Test
    void normalizeOriginDropsPathAndDefaultPort() {
        assertEquals("https://game.example.com", WebSocketAllowedOriginResolver.normalizeOrigin("https://game.example.com/Game"));
        assertEquals("http://demo.example.com", WebSocketAllowedOriginResolver.normalizeOrigin("http://demo.example.com:80/Game"));
        assertEquals("https://demo.example.com:8443", WebSocketAllowedOriginResolver.normalizeOrigin("https://demo.example.com:8443/Game"));
        assertEquals("", WebSocketAllowedOriginResolver.normalizeOrigin("not-a-url"));
    }
}
