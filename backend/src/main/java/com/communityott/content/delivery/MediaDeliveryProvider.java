package com.communityott.content.delivery;

import com.communityott.content.entity.VideoHlsPackage;

import java.time.Duration;

public interface MediaDeliveryProvider {

    /**
     * The delivery mode supported by this provider (LOCAL vs CDN).
     */
    DeliveryMode getMode();

    /**
     * Provider identifier name (e.g. MINIO_LOCAL, GENERIC_CDN, CLOUDFRONT, CLOUDFLARE).
     */
    String getProviderName();

    /**
     * Generates secure playback delivery information for the given HLS package.
     *
     * @param hlsPackage The ready HLS package entity
     * @param ttl        Time to live for playback authorization
     * @return PlaybackDeliveryInfo containing playback URL and expiration
     */
    PlaybackDeliveryInfo generateDeliveryInfo(VideoHlsPackage hlsPackage, Duration ttl);
}
