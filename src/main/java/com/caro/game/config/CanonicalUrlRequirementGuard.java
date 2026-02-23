package com.caro.game.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class CanonicalUrlRequirementGuard {
    private static final Logger log = LoggerFactory.getLogger(CanonicalUrlRequirementGuard.class);

    private static final String DEFAULT_HOST = "J2EE";
    private static final String DEFAULT_CONTEXT_PATH = "/Game";
    private static final int HTTP_PORT = 80;
    private static final int CONNECT_TIMEOUT_MS = (int) Duration.ofSeconds(2).toMillis();

    private final Environment env;

    public CanonicalUrlRequirementGuard(Environment env) {
        this.env = env;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validateCanonicalUrlRequirements() {
        if (isTestProfileActive()) {
            return;
        }

        boolean enforce = env.getProperty("app.runtime.canonical-url.enforce", Boolean.class, true);
        if (!enforce) {
            return;
        }

        String requiredHost = env.getProperty("app.runtime.canonical-url.host", DEFAULT_HOST).trim();
        String requiredContextPath = normalizeContextPath(
            env.getProperty("app.runtime.canonical-url.context-path", DEFAULT_CONTEXT_PATH)
        );
        String actualContextPath = normalizeContextPath(env.getProperty("server.servlet.context-path", ""));

        if (!requiredContextPath.equals(actualContextPath)) {
            throw fail(
                "Context path mismatch. Expected '" + requiredContextPath + "' but app is running with '" + actualContextPath + "'.",
                requiredHost, requiredContextPath
            );
        }

        validateHostMapping(requiredHost, requiredContextPath);

        boolean requirePort80 = env.getProperty("app.runtime.canonical-url.require-port-80", Boolean.class, true);
        if (requirePort80) {
            validatePort80(requiredHost, requiredContextPath);
        }

        boolean checkReverseProxy = env.getProperty("app.runtime.canonical-url.check-reverse-proxy", Boolean.class, true);
        if (checkReverseProxy) {
            validateReverseProxyRoute(requiredHost, requiredContextPath);
        }

        log.info("Canonical URL requirement satisfied: {}", displayUrl(requiredHost, requiredContextPath));
    }

    private boolean isTestProfileActive() {
        for (String profile : env.getActiveProfiles()) {
            if ("test".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    private void validateHostMapping(String host, String contextPath) {
        try {
            InetAddress address = InetAddress.getByName(host);
            if (!(address.isLoopbackAddress() || address.isAnyLocalAddress())) {
                throw fail(
                    "Host '" + host + "' resolves to " + address.getHostAddress() + " instead of local machine.",
                    host, contextPath
                );
            }
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw fail(
                "Host '" + host + "' does not resolve. Add it to the OS hosts file.",
                host, contextPath
            );
        }
    }

    private void validatePort80(String host, String contextPath) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", HTTP_PORT), CONNECT_TIMEOUT_MS);
        } catch (Exception ex) {
            throw fail(
                "Port 80 is not listening on localhost. Start Apache/Laragon reverse proxy before running the app.",
                host, contextPath
            );
        }
    }

    private void validateReverseProxyRoute(String host, String contextPath) {
        String path = contextPath + "/api/connectivity/ping";
        String request = "GET " + path + " HTTP/1.1\r\n"
            + "Host: " + host + "\r\n"
            + "Connection: close\r\n\r\n";

        String lastStatusLine = null;
        for (int attempt = 1; attempt <= 8; attempt++) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", HTTP_PORT), CONNECT_TIMEOUT_MS);
                socket.setSoTimeout(CONNECT_TIMEOUT_MS);
                OutputStream out = socket.getOutputStream();
                out.write(request.getBytes(StandardCharsets.US_ASCII));
                out.flush();

                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII)
                )) {
                    lastStatusLine = reader.readLine();
                }

                if (lastStatusLine != null && lastStatusLine.contains(" 200 ")) {
                    return;
                }
            } catch (Exception ignored) {
                // Retry a few times in case Apache and app start nearly at the same time.
            }

            try {
                Thread.sleep(300);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        throw fail(
            "Reverse proxy check failed for " + path + " via host '" + host + "'. Last response: "
                + (lastStatusLine == null ? "(no response)" : lastStatusLine),
            host, contextPath
        );
    }

    private IllegalStateException fail(String reason, String host, String contextPath) {
        String message = ""
            + "Canonical URL requirement failed.\n"
            + "Reason: " + reason + "\n"
            + "Required URL: " + displayUrl(host, contextPath) + "\n"
            + "Required machine setup (Windows/Laragon):\n"
            + "1) hosts file must contain: 127.0.0.1 " + host + "\n"
            + "2) Apache/Laragon must listen on port 80\n"
            + "3) Apache reverse proxy must map " + contextPath + " -> http://127.0.0.1:8080" + contextPath + "\n"
            + "4) If available, run project helper script as Administrator: powershell -ExecutionPolicy Bypass -File .\\scripts\\setup-j2ee-domain.ps1";
        return new IllegalStateException(message);
    }

    private String displayUrl(String host, String contextPath) {
        return "http://" + host + ("/".equals(contextPath) ? "/" : contextPath + "/");
    }

    private String normalizeContextPath(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || "/".equals(value)) {
            return "/";
        }
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        while (value.length() > 1 && value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
