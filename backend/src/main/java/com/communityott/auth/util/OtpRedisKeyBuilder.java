package com.communityott.auth.util;

import com.communityott.auth.entity.OtpPurpose;

/**
 * Centralized builder for Redis key namespaces used by the OTP service.
 */
public final class OtpRedisKeyBuilder {

    private static final String PREFIX = "communityott:otp";

    private OtpRedisKeyBuilder() {
        // Utility class
    }

    /**
     * Key for storing active OTP state payload in Redis.
     * Example: {@code communityott:otp:LOGIN:a1b2c3...}
     */
    public static String getOtpKey(OtpPurpose purpose, String identifierHash) {
        return String.format("%s:%s:%s", PREFIX, purpose.name(), identifierHash);
    }

    /**
     * Key for enforcing resend cooldown (e.g. 60s).
     * Example: {@code communityott:otp:cooldown:LOGIN:a1b2c3...}
     */
    public static String getCooldownKey(OtpPurpose purpose, String identifierHash) {
        return String.format("%s:cooldown:%s:%s", PREFIX, purpose.name(), identifierHash);
    }

    /**
     * Key for tracking hourly OTP request rate limits.
     * Example: {@code communityott:otp:ratelimit:request:a1b2c3...}
     */
    public static String getRequestRateLimitKey(String identifierHash) {
        return String.format("%s:ratelimit:request:%s", PREFIX, identifierHash);
    }
}
