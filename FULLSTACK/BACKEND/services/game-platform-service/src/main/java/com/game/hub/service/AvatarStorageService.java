package com.game.hub.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;

@Service
public class AvatarStorageService {
    public static final long MAX_AVATAR_BYTES = 406L * 1024L * 1024L;

    public StoreResult store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return StoreResult.error("Vui long chon tep avatar");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            return StoreResult.error("Avatar vuot qua gioi han 406MB");
        }

        try {
            byte[] data = file.getBytes();
            if (data.length == 0) {
                return StoreResult.error("Tep avatar trong");
            }
            if (data.length > MAX_AVATAR_BYTES) {
                return StoreResult.error("Avatar vuot qua gioi han 406MB");
            }
            String contentType = normalizeContentType(file.getContentType());
            String originalFileName = trimToNull(file.getOriginalFilename());
            return StoreResult.ok(data, contentType, originalFileName, data.length);
        } catch (IOException ex) {
            return StoreResult.error("Khong the doc tep avatar. Vui long thu lai");
        }
    }

    private String normalizeContentType(String contentType) {
        String normalized = trimToNull(contentType);
        if (normalized == null) {
            return "application/octet-stream";
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record StoreResult(boolean success,
                              String error,
                              byte[] binaryData,
                              String contentType,
                              String originalFileName,
                              long sizeBytes) {
        public static StoreResult ok(byte[] binaryData, String contentType, String originalFileName, long sizeBytes) {
            return new StoreResult(true, null, binaryData, contentType, originalFileName, sizeBytes);
        }

        public static StoreResult error(String error) {
            return new StoreResult(false, error, null, null, null, 0L);
        }
    }
}
