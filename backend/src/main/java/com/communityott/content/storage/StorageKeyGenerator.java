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

    public String generateHlsMasterKey(Long contentId, Long videoAssetId) {
        return String.format("hls/%d/%d/master.m3u8", contentId, videoAssetId);
    }

    public String generateHlsVariantPlaylistKey(Long contentId, Long videoAssetId, String resolution) {
        return String.format("hls/%d/%d/%s/index.m3u8", contentId, videoAssetId, sanitizeResolution(resolution));
    }

    public String generateHlsInitSegmentKey(Long contentId, Long videoAssetId, String resolution) {
        return String.format("hls/%d/%d/%s/init.mp4", contentId, videoAssetId, sanitizeResolution(resolution));
    }

    public String generateHlsMediaSegmentKey(Long contentId, Long videoAssetId, String resolution, String segmentFilename) {
        return String.format("hls/%d/%d/%s/%s", contentId, videoAssetId, sanitizeResolution(resolution), segmentFilename);
    }

    private String sanitizeResolution(String resolution) {
        if (resolution == null || resolution.isBlank()) {
            return "default";
        }
        return resolution.trim().toLowerCase(Locale.ROOT);
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
