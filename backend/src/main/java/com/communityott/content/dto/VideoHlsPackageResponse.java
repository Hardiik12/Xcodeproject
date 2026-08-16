package com.communityott.content.dto;

import com.communityott.content.entity.HlsPackageStatus;
import com.communityott.content.entity.VideoHlsPackage;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
public class VideoHlsPackageResponse {

    private final Long id;
    private final Long videoAssetId;
    private final Long processingJobId;
    private final String masterPlaylistKey;
    private final String storageBucket;
    private final HlsPackageStatus status;
    private final Integer variantCount;
    private final Integer targetDurationSeconds;
    private final String errorCode;
    private final String errorMessage;
    private final List<VideoHlsVariantResponse> variants;
    private final Instant createdAt;
    private final Instant completedAt;

    public static VideoHlsPackageResponse fromEntity(VideoHlsPackage pkg) {
        if (pkg == null) {
            return null;
        }
        List<VideoHlsVariantResponse> variantResponses = pkg.getVariants() != null
                ? pkg.getVariants().stream().map(VideoHlsVariantResponse::fromEntity).toList()
                : List.of();

        return VideoHlsPackageResponse.builder()
                .id(pkg.getId())
                .videoAssetId(pkg.getVideoAsset() != null ? pkg.getVideoAsset().getId() : null)
                .processingJobId(pkg.getProcessingJobId())
                .masterPlaylistKey(pkg.getMasterPlaylistKey())
                .storageBucket(pkg.getStorageBucket())
                .status(pkg.getStatus())
                .variantCount(pkg.getVariantCount())
                .targetDurationSeconds(pkg.getTargetDurationSeconds())
                .errorCode(pkg.getErrorCode())
                .errorMessage(pkg.getErrorMessage())
                .variants(variantResponses)
                .createdAt(pkg.getCreatedAt())
                .completedAt(pkg.getCompletedAt())
                .build();
    }
}
