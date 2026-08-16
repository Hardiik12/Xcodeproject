package com.communityott.content.dto;

import com.communityott.content.entity.VideoHlsVariant;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PlaybackRenditionDto {

    private final String resolution;
    private final Integer width;
    private final Integer height;
    private final Long bandwidthBps;
    private final Long averageBandwidthBps;
    private final String codecs;
    private final Double frameRate;

    public static PlaybackRenditionDto fromEntity(VideoHlsVariant variant) {
        if (variant == null) return null;
        return PlaybackRenditionDto.builder()
                .resolution(variant.getResolution())
                .width(variant.getWidth())
                .height(variant.getHeight())
                .bandwidthBps(variant.getBandwidthBps())
                .averageBandwidthBps(variant.getAverageBandwidthBps())
                .codecs(variant.getCodecs())
                .frameRate(variant.getFrameRate())
                .build();
    }
}
