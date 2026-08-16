package com.communityott.content.processing;

import com.communityott.content.entity.VideoResolution;
import lombok.Builder;
import lombok.Getter;

/**
 * Definition of transcoding parameters for a specific rendition profile.
 */
@Getter
@Builder
public class TranscodeProfile {

    private final VideoResolution resolution;
    private final int width;
    private final int height;
    private final int videoBitrateKbps;
    private final int audioBitrateKbps;
    private final String preset;
    private final String videoCodec;
    private final String audioCodec;

    public static TranscodeProfile fromResolution(VideoResolution res) {
        return TranscodeProfile.builder()
                .resolution(res)
                .width(res.getWidth())
                .height(res.getHeight())
                .videoBitrateKbps(res.getVideoBitrateKbps())
                .audioBitrateKbps(res.getAudioBitrateKbps())
                .preset(res.getFfmpegPreset())
                .videoCodec("libx264")
                .audioCodec("aac")
                .build();
    }
}
