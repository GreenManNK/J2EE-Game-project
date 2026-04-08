package com.game.hub.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.hub.service.ExternalGameModuleConfig;
import com.game.hub.service.ExternalGameModuleService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalGameModuleGatewayControllerTest {
    private HttpServer httpServer;

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    @Test
    void shouldProxyExternalModuleApiThroughSameDomainPath() throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/remote/demo/status", this::handleStatus);
        httpServer.start();

        int port = httpServer.getAddress().getPort();
        Path registryFile = Files.createTempFile("external-module-gateway", ".json");
        ExternalGameModuleService externalService = new ExternalGameModuleService(
            new ObjectMapper(),
            registryFile,
            Duration.ofSeconds(5),
            Duration.ofSeconds(5),
            HttpClient.newHttpClient()
        );
        externalService.upsertModules(List.of(
            new ExternalGameModuleConfig(
                "go-racer",
                "Go Racer",
                "Go Racer",
                "Go module test.",
                "bi-controller",
                true,
                true,
                false,
                true,
                "Mo Go Racer",
                "https://go.example.com/play",
                List.of("Proxy api"),
                "external-api",
                "go",
                "api",
                "",
                "http://127.0.0.1:" + port + "/remote/demo",
                "http://127.0.0.1:" + port + "/manifest.json",
                false
            )
        ), false);

        ExternalGameModuleGatewayController controller = new ExternalGameModuleGatewayController(externalService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/games/external/go-racer/api/status");
        request.setRequestURI("/games/external/go-racer/api/status");
        request.setQueryString("room=R-1");
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCEPT, "application/json");

        var response = controller.get("go-racer", request, headers);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(new String(response.getBody(), StandardCharsets.UTF_8).contains("\"room\":\"R-1\""));
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String body = "{\"ok\":true,\"room\":\"" + (query == null ? "" : query.replace("room=", "")) + "\"}";
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }
}
