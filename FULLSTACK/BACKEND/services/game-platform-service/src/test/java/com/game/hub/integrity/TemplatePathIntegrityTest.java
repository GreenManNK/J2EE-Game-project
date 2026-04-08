package com.game.hub.integrity;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;

class TemplatePathIntegrityTest {

    private static final Pattern CONTROLLER_RETURN_PATTERN = Pattern.compile("return\\s+\"([^\"]+)\"");
    private static final Pattern FRAGMENT_REF_PATTERN = Pattern.compile("~\\{([^}]+)\\}");
    private static final Pattern LOCAL_ASSET_PATTERN = Pattern.compile("@\\{/((?:js|css|lib|music)/[^}]+)\\}");

    private static final Path MAIN_JAVA_ROOT = Path.of(
        "services", "game-platform-service", "src", "main", "java", "com", "game", "hub"
    );
    private static final Path TEMPLATES_ROOT = Path.of(
        "..", "FRONTEND", "apps", "user-web", "src", "main", "resources", "templates"
    );
    private static final Path STATIC_ROOT = Path.of(
        "..", "FRONTEND", "apps", "user-web", "src", "main", "resources", "static"
    );
    private static final Path REPO_ROOT = Path.of("").toAbsolutePath().normalize();

    @Test
    void controllerViewPathsShouldResolveToTemplates() throws IOException {
        List<String> missing = new ArrayList<>();
        int checked = 0;

        try (Stream<Path> files = Files.walk(MAIN_JAVA_ROOT)) {
            for (Path file : files.filter(this::isControllerJavaFile).toList()) {
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    Matcher matcher = CONTROLLER_RETURN_PATTERN.matcher(lines.get(i));
                    while (matcher.find()) {
                        String candidate = matcher.group(1);
                        if (!looksLikeThymeleafViewPath(candidate)) {
                            continue;
                        }
                        checked++;
                        Path expected = TEMPLATES_ROOT.resolve(candidate + ".html");
                        if (!Files.exists(expected)) {
                            missing.add("%s:%d -> %s (missing %s)".formatted(
                                rel(file), i + 1, candidate, rel(expected)));
                        }
                    }
                }
            }
        }

        assertFalse(checked == 0, "No controller view paths were checked; integrity test filter may be too strict");
        assertFalse(!missing.isEmpty(), "Missing controller view templates:\n" + String.join("\n", missing));
    }

    @Test
    void thymeleafTemplateReferencesShouldResolve() throws IOException {
        List<String> missing = new ArrayList<>();
        int checked = 0;

        try (Stream<Path> files = Files.walk(TEMPLATES_ROOT)) {
            for (Path file : files.filter(this::isHtmlFile).toList()) {
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    Matcher matcher = FRAGMENT_REF_PATTERN.matcher(lines.get(i));
                    while (matcher.find()) {
                        String expression = matcher.group(1).trim();
                        String templateRef = normalizeTemplateReference(expression);
                        if (templateRef == null) {
                            continue;
                        }
                        checked++;
                        Path expected = TEMPLATES_ROOT.resolve(templateRef + ".html");
                        if (!Files.exists(expected)) {
                            missing.add("%s:%d -> %s (missing %s)".formatted(
                                rel(file), i + 1, templateRef, rel(expected)));
                        }
                    }
                }
            }
        }

        assertFalse(checked == 0, "No Thymeleaf template references were checked");
        assertFalse(!missing.isEmpty(), "Missing Thymeleaf template references:\n" + String.join("\n", missing));
    }

    @Test
    void localTemplateAssetReferencesShouldResolve() throws IOException {
        List<String> missing = new ArrayList<>();
        int checked = 0;

        try (Stream<Path> files = Files.walk(TEMPLATES_ROOT)) {
            for (Path file : files.filter(this::isHtmlFile).toList()) {
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    Matcher matcher = LOCAL_ASSET_PATTERN.matcher(lines.get(i));
                    while (matcher.find()) {
                        String assetRef = matcher.group(1);
                        if (assetRef.contains("${")) {
                            continue;
                        }
                        String normalized = assetRef.replaceAll("\\(.*$", "");
                        checked++;
                        Path expected = STATIC_ROOT.resolve(normalized.replace('/', '\\'));
                        if (!Files.exists(expected)) {
                            missing.add("%s:%d -> /%s (missing %s)".formatted(
                                rel(file), i + 1, normalized, rel(expected)));
                        }
                    }
                }
            }
        }

        assertFalse(checked == 0, "No local template asset references were checked");
        assertFalse(!missing.isEmpty(), "Missing local assets referenced by templates:\n" + String.join("\n", missing));
    }

    private boolean isControllerJavaFile(Path path) {
        String fileName = path.getFileName().toString();
        return Files.isRegularFile(path) && fileName.endsWith("Controller.java");
    }

    private boolean isHtmlFile(Path path) {
        return Files.isRegularFile(path) && path.getFileName().toString().endsWith(".html");
    }

    private boolean looksLikeThymeleafViewPath(String value) {
        if (value == null || value.isBlank()) return false;
        if (value.startsWith("redirect:") || value.startsWith("forward:")) return false;
        if (value.startsWith("/")) return false;
        if (value.contains("?") || value.contains("{") || value.contains(" ")) return false;
        if (!value.contains("/")) return false;
        return !value.matches(".*\\.(jpg|jpeg|png|gif|svg)$");
    }

    private String normalizeTemplateReference(String expression) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        String templateRef = expression.split("::", 2)[0].trim();
        if (templateRef.isEmpty() || templateRef.contains("${")) {
            return null;
        }
        if (templateRef.startsWith("/")) {
            templateRef = templateRef.substring(1);
        }
        int paramsStart = templateRef.indexOf('(');
        if (paramsStart >= 0) {
            templateRef = templateRef.substring(0, paramsStart).trim();
        }
        return templateRef.isEmpty() ? null : templateRef;
    }

    private String rel(Path path) {
        Path abs = path.toAbsolutePath().normalize();
        return REPO_ROOT.relativize(abs).toString().replace('\\', '/');
    }
}
