package com.communityott.content.delivery;

import com.communityott.content.entity.VideoHlsPackage;
import com.communityott.content.storage.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component("minioMediaDeliveryProvider")
@Slf4j
@RequiredArgsConstructor
public class MinioMediaDeliveryProvider implements MediaDeliveryProvider {

    private final ObjectStorageService objectStorageService;

    @Override
    public DeliveryMode getMode() {
        return DeliveryMode.LOCAL;
    }

    @Override
    public String getProviderName() {
        return "MINIO_LOCAL";
    }

    @Override
    public PlaybackDeliveryInfo generateDeliveryInfo(VideoHlsPackage hlsPackage, Duration ttl) {
        String bucket = hlsPackage.getStorageBucket();
        String masterKey = hlsPackage.getMasterPlaylistKey();

        log.info("Generating MinIO presigned playback URL for packageId={}, bucket={}, key={}, ttl={}",
                hlsPackage.getId(), bucket, masterKey, ttl);

        String presignedUrl = objectStorageService.generatePresignedGetUrl(bucket, masterKey, ttl);
        Instant expiresAt = Instant.now().plus(ttl);

        return PlaybackDeliveryInfo.builder()
                .playbackUrl(presignedUrl)
                .protocol("HLS")
                .expiresAt(expiresAt)
                .deliveryMode(DeliveryMode.LOCAL)
                .deliveryProvider(getProviderName())
                .tokenOrSignature(null)
                .build();
    }
}
