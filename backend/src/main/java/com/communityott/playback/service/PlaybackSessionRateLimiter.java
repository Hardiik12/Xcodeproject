package com.communityott.playback.service;

import com.communityott.common.exception.PlaybackRateLimitedException;
import com.communityott.playback.config.PlaybackProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class PlaybackSessionRateLimiter {

    private static final String SESSION_CREATE_PREFIX = "communityott:ratelimit:playback:session:create:";
    private static final String PROGRESS_PREFIX = "communityott:ratelimit:playback:progress:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final PlaybackProperties properties;

    /**
     * Enforces rate limiting on playback session creation.
     *
     * @param userId Authenticated user ID
     */
    public void checkSessionCreationRateLimit(Long userId) {
        if (!properties.getRateLimit().isEnabled()) {
            return;
        }

        String userKey = userId != null ? String.valueOf(userId) : "anonymous";
        long currentMinute = Instant.now().getEpochSecond() / 60;
        String rateLimitKey = SESSION_CREATE_PREFIX + userKey + ":" + currentMinute;
        int maxCreations = properties.getRateLimit().getMaxSessionCreationsPerMinute();

        try {
            Long count = redisTemplate.opsForValue().increment(rateLimitKey);
            if (count != null && count == 1L) {
                redisTemplate.expire(rateLimitKey, 65, TimeUnit.SECONDS);
            }

            if (count != null && count > maxCreations) {
                log.warn("Playback session creation rate limit exceeded for userId='{}': {} > {} req/min",
                        userId, count, maxCreations);
                throw new PlaybackRateLimitedException(
                        String.format("Playback session creation rate limit exceeded. Max %d creations per minute.", maxCreations));
            }
        } catch (PlaybackRateLimitedException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Redis rate limit check failed for session creation userId='{}': {}. Falling back to permit.",
                    userId, e.getMessage());
        }
    }

    /**
     * Enforces rate limiting on heartbeats and progress updates.
     *
     * @param userId    Authenticated user ID
     * @param sessionId Public playback session ID
     */
    public void checkProgressRateLimit(Long userId, String sessionId) {
        if (!properties.getRateLimit().isEnabled()) {
            return;
        }

        String identifier = (userId != null ? userId : "user") + ":" + (sessionId != null ? sessionId : "session");
        long currentMinute = Instant.now().getEpochSecond() / 60;
        String rateLimitKey = PROGRESS_PREFIX + identifier + ":" + currentMinute;
        int maxRequests = properties.getRateLimit().getMaxRequestsPerMinute();

        try {
            Long count = redisTemplate.opsForValue().increment(rateLimitKey);
            if (count != null && count == 1L) {
                redisTemplate.expire(rateLimitKey, 65, TimeUnit.SECONDS);
            }

            if (count != null && count > maxRequests) {
                log.warn("Playback progress rate limit exceeded for userId='{}', sessionId='{}': {} > {} req/min",
                        userId, sessionId, count, maxRequests);
                throw new PlaybackRateLimitedException(
                        String.format("Playback progress/heartbeat rate limit exceeded. Max %d requests per minute.", maxRequests));
            }
        } catch (PlaybackRateLimitedException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Redis rate limit check failed for progress userId='{}', sessionId='{}': {}. Falling back to permit.",
                    userId, sessionId, e.getMessage());
        }
    }
}
