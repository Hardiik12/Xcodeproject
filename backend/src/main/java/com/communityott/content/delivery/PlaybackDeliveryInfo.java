package com.communityott.content.delivery;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class PlaybackDeliveryInfo {

    private final String playbackUrl;
    private final String protocol;
    private final Instant expiresAt;
    private final DeliveryMode deliveryMode;
    private final String deliveryProvider;
    private final String tokenOrSignature;
}
