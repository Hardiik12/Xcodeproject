package com.communityott.content.delivery;

import com.communityott.common.exception.PlaybackRateLimitedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class PlaybackRateLimiter {

    private static final String KEY_PREFIX = "communityott:ratelimit:playback:";
    private final RedisTemplate<String, Object> redisTemplate;
    private final MediaDeliveryProperties properties;

    /**
     * Enforces rate limiting on playback authorization URL generation.
     *
     * @param identifier Unique client identifier (e.g. user ID or IP address)
     * @throws PlaybackRateLimitedException if rate limit is exceeded
     */
    public void checkRateLimit(String identifier) {
        if (!properties.getRateLimit().isEnabled()) {
            return;
        }

        if (identifier == null || identifier.isBlank()) {
            identifier = "anonymous";
        }

        long currentMinute = Instant.now().getEpochSecond() / 60;
        String rateLimitKey = KEY_PREFIX + identifier + ":" + currentMinute;
        int maxRequests = properties.getRateLimit().getMaxRequestsPerMinute();

        try {
            Long count = redisTemplate.opsForValue().increment(rateLimitKey);
            if (count != null && count == 1L) {
                redisTemplate.expire(rateLimitKey, 65, TimeUnit.SECONDS);
            }

            if (count != null && count > maxRequests) {
                log.warn("Playback URL rate limit exceeded for identifier='{}': {} > {} req/min",
                        identifier, count, maxRequests);
                throw new PlaybackRateLimitedException(
                        String.format("Playback authorization request rate limit exceeded. Max %d requests per minute.", maxRequests));
            }
        } catch (PlaybackRateLimitedException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to check Redis rate limit for playback identifier='{}': {}. Allowing request as fallback.",
                    identifier, e.getMessage());
        }
    }
}
