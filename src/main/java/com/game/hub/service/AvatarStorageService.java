package com.game.hub.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AvatarStorageService {
    private static final long MAX_AVATAR_BYTES = 2L * 1024L * 1024L;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final Map<String, String> CONTENT_TYPE_TO_EXTENSION = Map.of(
        "image/jpeg", "jpg",
        "image/png", "png",
        "image/gif", "gif",
        "image/webp", "webp"
    );

    private final Path avatarDirectory;

    public AvatarStorageService(@Value("${app.upload.avatar-dir:uploads/avatars}") String avatarDirectory) {
        String configuredPath = (avatarDirectory == null || avatarDirectory.isBlank()) ? "uploads/avatars" : avatarDirectory.trim();
        this.avatarDirectory = Path.of(configuredPath).toAbsolutePath().normalize();
    }

    public StoreResult store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return StoreResult.error("Vui long chon file anh");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            return StoreResult.error("Anh vuot qua gioi han 2MB");
        }

        String extension = resolveExtension(file);
        if (extension == null) {
            return StoreResult.error("Chi ho tro JPG, PNG, GIF, WEBP");
        }

        try {
            Files.createDirectories(avatarDirectory);
            String fileName = UUID.randomUUID() + "." + extension;
            Path target = avatarDirectory.resolve(fileName).normalize();
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return StoreResult.ok("/uploads/avatars/" + fileName);
        } catch (IOException ex) {
            return StoreResult.error("Khong the luu anh. Vui long thu lai");
        }
    }

    private String resolveExtension(MultipartFile file) {
        String fromFileName = extensionOf(file.getOriginalFilename());
        if (fromFileName != null) {
            return fromFileName;
        }
        String contentType = file.getContentType();
        if (contentType == null) {
            return null;
        }
        return CONTENT_TYPE_TO_EXTENSION.get(contentType.toLowerCase());
    }

    private String extensionOf(String fileName) {
        if (fileName == null) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        String ext = fileName.substring(dot + 1).trim().toLowerCase();
        return ALLOWED_EXTENSIONS.contains(ext) ? ext : null;
    }

    public record StoreResult(boolean success, String error, String avatarPath) {
        public static StoreResult ok(String avatarPath) {
            return new StoreResult(true, null, avatarPath);
        }

        public static StoreResult error(String error) {
            return new StoreResult(false, error, null);
        }
    }
}
