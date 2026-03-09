package com.game.hub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalGameModuleServiceTest {
    private HttpServer httpServer;

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    @Test
    void previewModulesShouldNormalizeWithoutPersistingRegistry() throws Exception {
        Path registryFile = Files.createTempFile("external-module-preview", ".json");
        Files.deleteIfExists(registryFile);
        ExternalGameModuleService service = new ExternalGameModuleService(
            new ObjectMapper(),
            registryFile,
            Duration.ofSeconds(5),
            Duration.ofSeconds(5),
            HttpClient.newHttpClient()
        );

        ExternalGameModuleService.GameModulePreviewResult result = service.previewModules(List.of(
            new ExternalGameModuleConfig(
                "python-blast",
                "Python Blast",
                "",
                "",
                "",
                null,
                true,
                false,
                null,
                "",
                "https://games.example.com/python-blast",
                List.of(),
                "",
                "python",
                "",
                "",
                "https://api.example.com/python-blast",
                "",
                false
            )
        ));

        assertEquals(1, result.previewedModules());
        assertEquals("python-blast", result.modules().get(0).code());
        assertEquals("external-module", result.modules().get(0).sourceType());
        assertEquals("redirect", result.modules().get(0).detailMode());
        assertFalse(Files.exists(registryFile));
        assertTrue(service.listCatalogItems().isEmpty());
    }

    @Test
    void previewManifestUrlShouldReadRemoteManifestWithoutPersistingRegistry() throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/manifest.json", this::handleManifest);
        httpServer.start();

        int port = httpServer.getAddress().getPort();
        Path registryFile = Files.createTempFile("external-module-preview-url", ".json");
        Files.deleteIfExists(registryFile);
        ExternalGameModuleService service = new ExternalGameModuleService(
            new ObjectMapper(),
            registryFile,
            Duration.ofSeconds(5),
            Duration.ofSeconds(5),
            HttpClient.newHttpClient()
        );

        ExternalGameModuleService.GameModulePreviewResult result = service.previewManifestUrl(
            "http://127.0.0.1:" + port + "/manifest.json"
        );

        assertEquals(1, result.previewedModules());
        assertEquals("go-racer", result.modules().get(0).code());
        assertEquals("go", result.modules().get(0).runtime());
        assertEquals("http://127.0.0.1:" + port + "/manifest.json", result.manifestUrl());
        assertFalse(Files.exists(registryFile));
        assertTrue(service.listConfigurations().isEmpty());
    }

    private void handleManifest(HttpExchange exchange) throws IOException {
        String body = """
            {
              "modules": [
                {
                  "code": "go-racer",
                  "displayName": "Go Racer",
                  "runtime": "go",
                  "sourceType": "external-api",
                  "detailMode": "api",
                  "apiBaseUrl": "https://api.example.com/go-racer"
                }
              ]
            }
            """;
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }
}
