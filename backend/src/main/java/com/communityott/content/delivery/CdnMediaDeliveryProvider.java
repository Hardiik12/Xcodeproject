package com.communityott.content.delivery;

import com.communityott.content.entity.VideoHlsPackage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component("cdnMediaDeliveryProvider")
@Slf4j
@RequiredArgsConstructor
public class CdnMediaDeliveryProvider implements MediaDeliveryProvider {

    private final MediaDeliveryProperties properties;

    @Override
    public DeliveryMode getMode() {
        return DeliveryMode.CDN;
    }

    @Override
    public String getProviderName() {
        return "CDN_GENERIC";
    }

    @Override
    public PlaybackDeliveryInfo generateDeliveryInfo(VideoHlsPackage hlsPackage, Duration ttl) {
        String baseUrl = properties.getCdn().getBaseUrl().replaceAll("/+$", "");
        String masterKey = hlsPackage.getMasterPlaylistKey().replaceAll("^/+", "");
        Instant expiresAt = Instant.now().plus(ttl);

        StringBuilder playbackUrl = new StringBuilder();
        playbackUrl.append(baseUrl).append("/").append(masterKey);

        String tokenOrSignature = null;
        if (properties.getCdn().isTokenAuthEnabled()) {
            long epochSeconds = expiresAt.getEpochSecond();
            tokenOrSignature = "exp=" + epochSeconds + "&kid=" + properties.getCdn().getSigningKeyId();
            playbackUrl.append("?").append(tokenOrSignature);
        }

        log.info("Generated CDN playback URL for packageId={}, key={}, url={}",
                hlsPackage.getId(), masterKey, playbackUrl);

        return PlaybackDeliveryInfo.builder()
                .playbackUrl(playbackUrl.toString())
                .protocol("HLS")
                .expiresAt(expiresAt)
                .deliveryMode(DeliveryMode.CDN)
                .deliveryProvider(getProviderName())
                .tokenOrSignature(tokenOrSignature)
                .build();
    }
}
