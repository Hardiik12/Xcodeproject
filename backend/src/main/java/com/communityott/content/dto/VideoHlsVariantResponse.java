package com.communityott.content.dto;

import com.communityott.content.entity.HlsVariantStatus;
import com.communityott.content.entity.VideoHlsVariant;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class VideoHlsVariantResponse {

    private final Long id;
    private final String resolution;
    private final Integer width;
    private final Integer height;
    private final String playlistKey;
    private final String initSegmentKey;
    private final Integer segmentCount;
    private final Integer targetDurationSeconds;
    private final Long bandwidthBps;
    private final Long averageBandwidthBps;
    private final String codecs;
    private final Double frameRate;
    private final HlsVariantStatus status;
    private final Instant createdAt;

    public static VideoHlsVariantResponse fromEntity(VideoHlsVariant variant) {
        if (variant == null) {
            return null;
        }
        return VideoHlsVariantResponse.builder()
                .id(variant.getId())
                .resolution(variant.getResolution())
                .width(variant.getWidth())
                .height(variant.getHeight())
                .playlistKey(variant.getPlaylistKey())
                .initSegmentKey(variant.getInitSegmentKey())
                .segmentCount(variant.getSegmentCount())
                .targetDurationSeconds(variant.getTargetDurationSeconds())
                .bandwidthBps(variant.getBandwidthBps())
                .averageBandwidthBps(variant.getAverageBandwidthBps())
                .codecs(variant.getCodecs())
                .frameRate(variant.getFrameRate())
                .status(variant.getStatus())
                .createdAt(variant.getCreatedAt())
                .build();
    }
}
