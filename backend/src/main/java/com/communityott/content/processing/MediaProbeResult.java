package com.communityott.content.processing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaProbeResult {
    private Integer durationSeconds;
    private Integer width;
    private Integer height;
    private Integer bitrateKbps;
    private String containerFormat;
    private String videoCodec;
    private String audioCodec;
    private String frameRate;
    private String rawJson;
    private boolean validMedia;
    private String validationError;
}
