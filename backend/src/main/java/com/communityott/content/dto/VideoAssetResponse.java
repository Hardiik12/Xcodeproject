package com.communityott.content.dto;

import com.communityott.content.entity.VideoAsset;
import com.communityott.content.entity.VideoAssetStatus;
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
public class VideoAssetResponse {

    private Long id;
    private Long contentId;
    private String originalFilename;
    private Long fileSizeBytes;
    private String contentType;
    private String checksumSha256;
    private String storageBucket;
    private String storageKey;
    private VideoAssetStatus status;
    private Integer durationSeconds;
    private Integer width;
    private Integer height;
    private Integer bitrateKbps;
    private Long createdBy;
    private Long updatedBy;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;

    public static VideoAssetResponse fromEntity(VideoAsset asset) {
        if (asset == null) {
            return null;
        }
        return VideoAssetResponse.builder()
                .id(asset.getId())
                .contentId(asset.getContent() != null ? asset.getContent().getId() : null)
                .originalFilename(asset.getOriginalFilename())
                .fileSizeBytes(asset.getFileSizeBytes())
                .contentType(asset.getContentType())
                .checksumSha256(asset.getChecksumSha256())
                .storageBucket(asset.getStorageBucket())
                .storageKey(asset.getStorageKey())
                .status(asset.getStatus())
                .durationSeconds(asset.getDurationSeconds())
                .width(asset.getWidth())
                .height(asset.getHeight())
                .bitrateKbps(asset.getBitrateKbps())
                .createdBy(asset.getCreatedBy())
                .updatedBy(asset.getUpdatedBy())
                .createdAt(asset.getCreatedAt())
                .updatedAt(asset.getUpdatedAt())
                .version(asset.getVersion())
                .build();
    }
}
