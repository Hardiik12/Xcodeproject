package com.communityott.content.dto;

import com.communityott.content.entity.RenditionStatus;
import com.communityott.content.entity.VideoRendition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoRenditionResponse {

    private Long id;
    private Long videoAssetId;
    private String resolution;
    private Integer width;
    private Integer height;
    private String videoCodec;
    private String audioCodec;
    private Integer bitrateKbps;
    private Integer audioBitrateKbps;
    private Double frameRate;
    private Long fileSizeBytes;
    private String storageBucket;
    private String storageKey;
    private String checksumSha256;
    private Integer durationSeconds;
    private RenditionStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public static VideoRenditionResponse fromEntity(VideoRendition entity) {
        if (entity == null) {
            return null;
        }
        return VideoRenditionResponse.builder()
                .id(entity.getId())
                .videoAssetId(entity.getVideoAsset() != null ? entity.getVideoAsset().getId() : null)
                .resolution(entity.getResolution())
                .width(entity.getWidth())
                .height(entity.getHeight())
                .videoCodec(entity.getVideoCodec())
                .audioCodec(entity.getAudioCodec())
                .bitrateKbps(entity.getBitrateKbps())
                .audioBitrateKbps(entity.getAudioBitrateKbps())
                .frameRate(entity.getFrameRate())
                .fileSizeBytes(entity.getFileSizeBytes())
                .storageBucket(entity.getStorageBucket())
                .storageKey(entity.getStorageKey())
                .checksumSha256(entity.getChecksumSha256())
                .durationSeconds(entity.getDurationSeconds())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
