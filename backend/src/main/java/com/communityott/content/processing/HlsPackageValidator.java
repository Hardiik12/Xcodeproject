package com.communityott.content.processing;

import com.communityott.common.exception.VideoProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

@Slf4j
@Component
public class HlsPackageValidator {

    /**
     * Validates that variant playlist and segments conform to VOD fMP4 HLS standard.
     */
    public void validateVariantPackage(File variantDir, File playlistFile, File initFile, List<File> mediaSegments) {
        if (playlistFile == null || !playlistFile.exists() || playlistFile.length() == 0) {
            throw new VideoProcessingException("Variant playlist index.m3u8 does not exist or is empty");
        }

        if (initFile == null || !initFile.exists() || initFile.length() == 0) {
            throw new VideoProcessingException("HLS initialization segment init.mp4 does not exist or is empty");
        }

        if (mediaSegments == null || mediaSegments.isEmpty()) {
            throw new VideoProcessingException("HLS media segments list is empty");
        }

        for (File segment : mediaSegments) {
            if (!segment.exists() || segment.length() == 0) {
                throw new VideoProcessingException("HLS media segment missing or zero-byte: " + segment.getName());
            }
        }

        try {
            String playlistContent = Files.readString(playlistFile.toPath());
            validatePlaylistSyntax(playlistContent, mediaSegments.size());
        } catch (IOException e) {
            throw new VideoProcessingException("Failed to read variant playlist for validation: " + e.getMessage());
        }
    }

    /**
     * Validates master playlist content.
     */
    public void validateMasterPlaylist(String masterPlaylistContent, int expectedVariantCount) {
        if (masterPlaylistContent == null || masterPlaylistContent.isBlank()) {
            throw new VideoProcessingException("Master playlist content is empty");
        }

        if (!masterPlaylistContent.contains("#EXTM3U")) {
            throw new VideoProcessingException("Master playlist missing required #EXTM3U header");
        }

        if (!masterPlaylistContent.contains("#EXT-X-STREAM-INF")) {
            throw new VideoProcessingException("Master playlist contains no #EXT-X-STREAM-INF entries");
        }

        long streamInfCount = masterPlaylistContent.lines()
                .filter(line -> line.startsWith("#EXT-X-STREAM-INF"))
                .count();

        if (streamInfCount < expectedVariantCount) {
            throw new VideoProcessingException(
                    String.format("Master playlist contains %d stream entries, but expected %d", streamInfCount, expectedVariantCount));
        }
    }

    private void validatePlaylistSyntax(String content, int segmentCount) {
        if (!content.contains("#EXTM3U")) {
            throw new VideoProcessingException("Variant playlist missing #EXTM3U header");
        }
        if (!content.contains("#EXT-X-TARGETDURATION")) {
            throw new VideoProcessingException("Variant playlist missing #EXT-X-TARGETDURATION tag");
        }
        if (!content.contains("#EXT-X-ENDLIST")) {
            throw new VideoProcessingException("VOD variant playlist missing required #EXT-X-ENDLIST tag");
        }
        if (!content.contains("init.mp4") && !content.contains("#EXT-X-MAP")) {
            throw new VideoProcessingException("fMP4 variant playlist missing initialization segment reference");
        }

        long extinfCount = content.lines()
                .filter(line -> line.startsWith("#EXTINF"))
                .count();

        if (extinfCount == 0 || extinfCount != segmentCount) {
            log.warn("Variant playlist segment count mismatch: #EXTINF count is {}, media segment files count is {}",
                    extinfCount, segmentCount);
        }
    }
}
