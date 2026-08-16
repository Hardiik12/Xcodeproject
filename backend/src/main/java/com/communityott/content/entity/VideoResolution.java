package com.communityott.content.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Standard OTT video resolution ladder presets.
 */
@Getter
@RequiredArgsConstructor
public enum VideoResolution {

    RES_1080P("1080p", 1920, 1080, 4800, 192, "medium"),
    RES_720P("720p", 1280, 720, 2600, 128, "medium"),
    RES_480P("480p", 854, 480, 1400, 128, "fast"),
    RES_360P("360p", 640, 360, 800, 96, "fast"),
    RES_144P("144p", 256, 144, 250, 64, "veryfast");

    private final String label;
    private final int width;
    private final int height;
    private final int videoBitrateKbps;
    private final int audioBitrateKbps;
    private final String ffmpegPreset;

    public static VideoResolution fromLabel(String label) {
        for (VideoResolution res : values()) {
            if (res.label.equalsIgnoreCase(label)) {
                return res;
            }
        }
        throw new IllegalArgumentException("Unknown video resolution label: " + label);
    }

    /**
     * Determines applicable resolutions for a given source video height.
     * Prevents upscaling (only returns resolutions with height <= sourceHeight).
     * Guarantees at least the lowest resolution preset is included.
     *
     * @param sourceHeight height of the probed source video in pixels
     * @return filtered list of video resolutions ordered from highest to lowest
     */
    public static List<VideoResolution> getLadderForSource(int sourceHeight) {
        List<VideoResolution> ladder = Arrays.stream(values())
                .filter(res -> res.getHeight() <= sourceHeight)
                .collect(Collectors.toList());

        if (ladder.isEmpty()) {
            ladder = List.of(RES_144P);
        }
        return ladder;
    }
}
