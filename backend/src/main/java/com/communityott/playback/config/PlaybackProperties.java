package com.communityott.playback.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for OTT video playback sessions and watch progress.
 */
@Configuration
@ConfigurationProperties(prefix = "communityott.playback")
@Getter
@Setter
public class PlaybackProperties {

    /**
     * Threshold percentage (0.0 - 100.0) beyond which a video is considered fully completed (default: 95.0%).
     */
    private double completionThresholdPercent = 95.0;

    /**
     * Inactivity timeout in minutes after which a session without heartbeats is marked EXPIRED (default: 5 minutes).
     */
    private int sessionInactivityTimeoutMinutes = 5;

    /**
     * Rate limiting settings for playback session creation and heartbeat/progress updates.
     */
    private RateLimitProperties rateLimit = new RateLimitProperties();

    @Getter
    @Setter
    public static class RateLimitProperties {
        private boolean enabled = true;
        private int maxSessionCreationsPerMinute = 15;
        private int maxRequestsPerMinute = 60;
    }
}
