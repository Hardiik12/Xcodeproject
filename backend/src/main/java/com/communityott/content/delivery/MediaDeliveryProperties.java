package com.communityott.content.delivery;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "communityott.media.delivery")
@Getter
@Setter
public class MediaDeliveryProperties {

    /**
     * Active media delivery mode: LOCAL (MinIO presigned URLs) or CDN (CDN edge delivery).
     */
    private DeliveryMode mode = DeliveryMode.LOCAL;

    /**
     * Time-to-live for playback URLs in seconds (default: 15 minutes = 900 seconds).
     */
    private long playbackUrlTtlSeconds = 900L;

    /**
     * CDN delivery settings.
     */
    private CdnProperties cdn = new CdnProperties();

    /**
     * Rate limiting configuration for playback URL authorization.
     */
    private RateLimitProperties rateLimit = new RateLimitProperties();

    @Getter
    @Setter
    public static class CdnProperties {
        private String baseUrl = "https://cdn.communityott.com";
        private String signingKeyId = "";
        private String signingPrivateKey = "";
        private boolean tokenAuthEnabled = false;
    }

    @Getter
    @Setter
    public static class RateLimitProperties {
        private boolean enabled = true;
        private int maxRequestsPerMinute = 30;
    }
}
