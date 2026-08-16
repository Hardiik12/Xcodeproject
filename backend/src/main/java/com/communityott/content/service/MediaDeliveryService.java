package com.communityott.content.service;

import com.communityott.common.exception.ContentNotFoundException;
import com.communityott.common.exception.VideoNotReadyException;
import com.communityott.content.delivery.CdnMediaDeliveryProvider;
import com.communityott.content.delivery.DeliveryMode;
import com.communityott.content.delivery.MediaDeliveryProperties;
import com.communityott.content.delivery.MediaDeliveryProvider;
import com.communityott.content.delivery.MinioMediaDeliveryProvider;
import com.communityott.content.delivery.PlaybackDeliveryInfo;
import com.communityott.content.delivery.PlaybackRateLimiter;
import com.communityott.content.dto.PlaybackResponse;
import com.communityott.content.entity.Content;
import com.communityott.content.entity.VideoAsset;
import com.communityott.content.entity.VideoAssetStatus;
import com.communityott.content.entity.VideoHlsPackage;
import com.communityott.content.entity.VideoHlsVariant;
import com.communityott.content.repository.ContentRepository;
import com.communityott.content.repository.VideoAssetRepository;
import com.communityott.content.repository.VideoHlsPackageRepository;
import com.communityott.content.repository.VideoHlsVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class MediaDeliveryService {

    private final ContentRepository contentRepository;
    private final VideoAssetRepository videoAssetRepository;
    private final VideoHlsPackageRepository videoHlsPackageRepository;
    private final VideoHlsVariantRepository videoHlsVariantRepository;
    private final ContentAccessService contentAccessService;
    private final PlaybackRateLimiter playbackRateLimiter;
    private final MinioMediaDeliveryProvider minioMediaDeliveryProvider;
    private final CdnMediaDeliveryProvider cdnMediaDeliveryProvider;
    private final MediaDeliveryProperties deliveryProperties;

    /**
     * Resolves, authorizes, and generates a secure playback URL for the given content item.
     *
     * @param contentId      Content ID requested by consumer
     * @param userIdentifier Authenticated user identifier (user ID or principal name) for rate limiting & audit
     * @return PlaybackResponse containing safe playback metadata and time-limited playback URL
     */
    @Transactional(readOnly = true)
    public PlaybackResponse getPlaybackInfo(Long contentId, String userIdentifier) {
        log.info("Processing playback authorization request: contentId={}, userIdentifier={}", contentId, userIdentifier);

        // 1. Rate Limiting Check
        playbackRateLimiter.checkRateLimit(userIdentifier);

        // 2. Resolve & Validate Content
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new ContentNotFoundException(contentId));
        contentAccessService.validateContentPlayable(content);

        // 3. Resolve & Validate Video Asset
        List<VideoAsset> assets = videoAssetRepository.findByContentIdOrderByCreatedAtDesc(contentId);
        VideoAsset readyAsset = assets.stream()
                .filter(a -> a.getStatus() == VideoAssetStatus.READY)
                .findFirst()
                .orElseThrow(() -> new VideoNotReadyException("No playable video asset found for content ID: " + contentId));
        contentAccessService.validateVideoAssetPlayable(readyAsset);

        // 4. Resolve & Validate HLS Package
        VideoHlsPackage hlsPackage = videoHlsPackageRepository.findByVideoAssetId(readyAsset.getId())
                .orElseThrow(() -> new VideoNotReadyException("HLS package not found for video asset ID: " + readyAsset.getId()));
        contentAccessService.validateHlsPackagePlayable(hlsPackage);

        // 5. Fetch Available Rendition Variants
        List<VideoHlsVariant> variants = videoHlsVariantRepository.findByHlsPackageIdOrderByHeightDesc(hlsPackage.getId());

        // 6. Select Active Delivery Provider
        MediaDeliveryProvider provider = resolveDeliveryProvider();
        Duration ttl = Duration.ofSeconds(deliveryProperties.getPlaybackUrlTtlSeconds());

        // 7. Generate Playback Delivery Info
        PlaybackDeliveryInfo deliveryInfo = provider.generateDeliveryInfo(hlsPackage, ttl);

        log.info("Successfully generated playback URL for contentId={}, provider={}, mode={}",
                contentId, deliveryInfo.getDeliveryProvider(), deliveryInfo.getDeliveryMode());

        return PlaybackResponse.of(content, readyAsset, hlsPackage, variants, deliveryInfo);
    }

    /**
     * Resolves active delivery provider based on configured delivery mode.
     */
    public MediaDeliveryProvider resolveDeliveryProvider() {
        if (deliveryProperties.getMode() == DeliveryMode.CDN) {
            return cdnMediaDeliveryProvider;
        }
        return minioMediaDeliveryProvider;
    }
}
