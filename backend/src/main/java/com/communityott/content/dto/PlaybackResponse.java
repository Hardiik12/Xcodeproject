package com.communityott.content.dto;

import com.communityott.content.delivery.PlaybackDeliveryInfo;
import com.communityott.content.entity.Content;
import com.communityott.content.entity.VideoAsset;
import com.communityott.content.entity.VideoHlsPackage;
import com.communityott.content.entity.VideoHlsVariant;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class PlaybackResponse {

    private final Long contentId;
    private final String title;
    private final Long videoAssetId;
    private final String protocol;
    private final String playbackUrl;
    private final Instant expiresAt;
    private final Integer durationSeconds;
    private final String deliveryMode;
    private final String deliveryProvider;
    private final List<PlaybackRenditionDto> availableRenditions;

    public static PlaybackResponse of(Content content, VideoAsset videoAsset, VideoHlsPackage hlsPackage,
                                     List<VideoHlsVariant> variants, PlaybackDeliveryInfo deliveryInfo) {
        List<PlaybackRenditionDto> renditionDtos = (variants != null)
                ? variants.stream().map(PlaybackRenditionDto::fromEntity).collect(Collectors.toList())
                : Collections.emptyList();

        return PlaybackResponse.builder()
                .contentId(content.getId())
                .title(content.getTitle())
                .videoAssetId(videoAsset.getId())
                .protocol(deliveryInfo.getProtocol())
                .playbackUrl(deliveryInfo.getPlaybackUrl())
                .expiresAt(deliveryInfo.getExpiresAt())
                .durationSeconds(videoAsset.getDurationSeconds() != null ? videoAsset.getDurationSeconds() : content.getDurationSeconds())
                .deliveryMode(deliveryInfo.getDeliveryMode().name())
                .deliveryProvider(deliveryInfo.getDeliveryProvider())
                .availableRenditions(renditionDtos)
                .build();
    }
}
