package com.communityott.content.storage;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class StorageKeyGenerator {

    public String generateSourceKey(Long contentId, String checksumSha256, String originalFilename) {
        String sanitizedFilename = sanitizeFilename(originalFilename);
        String prefix = (checksumSha256 != null && checksumSha256.length() >= 8)
                ? checksumSha256.substring(0, 8)
                : "source";
        return String.format("sources/%d/%s_%s", contentId, prefix, sanitizedFilename);
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "video.mp4";
        }
        // Replace spaces and special characters with underscore
        return filename.trim()
                .replaceAll("[^a-zA-Z0-9._-]", "_")
                .toLowerCase(Locale.ROOT);
    }
}
