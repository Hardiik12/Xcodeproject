package com.communityott.content.processing;

import com.communityott.common.exception.VideoProcessingException;
import com.communityott.content.entity.VideoHlsVariant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Slf4j
@Component
public class HlsManifestGenerator {

    /**
     * Generates a standard HLS VOD master playlist (#EXTM3U) referencing variant playlists.
     *
     * @param variants List of ready HLS variants
     * @return Master playlist content as UTF-8 string
     */
    public String generateMasterPlaylist(List<VideoHlsVariant> variants) {
        if (variants == null || variants.isEmpty()) {
            throw new VideoProcessingException("Cannot generate master playlist with 0 variants");
        }

        // Sort descending by bandwidth/height (standard OTT master playlist convention)
        List<VideoHlsVariant> sortedVariants = variants.stream()
                .sorted(Comparator.comparing(VideoHlsVariant::getHeight).reversed())
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("#EXTM3U\n");
        sb.append("#EXT-X-VERSION:7\n");
        sb.append("#EXT-X-INDEPENDENT-SEGMENTS\n\n");

        for (VideoHlsVariant variant : sortedVariants) {
            String relativeUri = sanitizeRelativePath(variant.getResolution() + "/index.m3u8");

            sb.append("#EXT-X-STREAM-INF:");
            sb.append("BANDWIDTH=").append(variant.getBandwidthBps());

            if (variant.getAverageBandwidthBps() != null && variant.getAverageBandwidthBps() > 0) {
                sb.append(",AVERAGE-BANDWIDTH=").append(variant.getAverageBandwidthBps());
            }

            sb.append(",RESOLUTION=").append(variant.getWidth()).append("x").append(variant.getHeight());

            if (variant.getCodecs() != null && !variant.getCodecs().isBlank()) {
                sb.append(",CODECS=\"").append(variant.getCodecs()).append("\"");
            }

            if (variant.getFrameRate() != null && variant.getFrameRate() > 0) {
                sb.append(String.format(Locale.ROOT, ",FRAME-RATE=%.3f", variant.getFrameRate()));
            }

            sb.append("\n");
            sb.append(relativeUri).append("\n\n");
        }

        String masterPlaylist = sb.toString();
        validateMasterPlaylistSecurity(masterPlaylist);
        return masterPlaylist;
    }

    private String sanitizeRelativePath(String path) {
        if (path == null || path.isBlank()) {
            throw new VideoProcessingException("Variant playlist URI cannot be blank");
        }
        if (path.contains("..") || path.startsWith("/") || path.contains("://")) {
            throw new VideoProcessingException("Security violation: Invalid variant URI detected: " + path);
        }
        return path;
    }

    private void validateMasterPlaylistSecurity(String content) {
        if (content.contains("../") || content.contains("..\\") || content.contains("file://") || content.contains("http://") || content.contains("https://")) {
            throw new VideoProcessingException("Security validation failed for generated master playlist");
        }
    }
}
