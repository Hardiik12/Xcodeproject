package com.communityott.auth.service;

import com.communityott.auth.dto.OtpRedisData;
import com.communityott.auth.dto.OtpRequestResult;
import com.communityott.auth.dto.OtpVerificationResult;
import com.communityott.auth.entity.AuthIdentifierType;
import com.communityott.auth.entity.OtpPurpose;
import com.communityott.auth.entity.OtpRequest;
import com.communityott.auth.entity.OtpRequestStatus;
import com.communityott.auth.repository.OtpRequestRepository;
import com.communityott.auth.util.IdentifierNormalizer;
import com.communityott.auth.util.OtpCryptoUtils;
import com.communityott.auth.util.OtpRedisKeyBuilder;
import com.communityott.common.exception.OtpAlreadyUsedException;
import com.communityott.common.exception.OtpCooldownException;
import com.communityott.common.exception.OtpExpiredException;
import com.communityott.common.exception.OtpInvalidException;
import com.communityott.common.exception.OtpMaxAttemptsException;
import com.communityott.common.exception.OtpRateLimitedException;
import com.communityott.user.entity.User;
import com.communityott.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Core OTP Service managing cryptographically secure OTP generation, HMAC-SHA256 hashing,
 * Redis active state storage, resend cooldowns, attempt limits, rate limiting, and PostgreSQL audit updates.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private final OtpRequestRepository otpRequestRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${communityott.security.otp-secret}")
    private String otpSecret;

    @Value("${communityott.auth.otp.length:6}")
    private int otpLength;

    @Value("${communityott.auth.otp.ttl-seconds:300}")
    private long ttlSeconds;

    @Value("${communityott.auth.otp.max-attempts:5}")
    private int maxAttempts;

    @Value("${communityott.auth.otp.resend-cooldown-seconds:60}")
    private long resendCooldownSeconds;

    @Value("${communityott.auth.otp.request-limit-per-hour:5}")
    private int requestLimitPerHour;

    /**
     * Issues a new secure OTP for a normalized identifier and purpose.
     *
     * @param identifierType EMAIL or PHONE
     * @param rawIdentifier raw user input identifier string
     * @param purpose LOGIN, REGISTRATION, or ACCOUNT_RECOVERY
     * @return OtpRequestResult containing request ID and expiration metadata
     */
    @Transactional
    public OtpRequestResult requestOtp(AuthIdentifierType identifierType, String rawIdentifier, OtpPurpose purpose) {
        String identifier = IdentifierNormalizer.normalize(identifierType, rawIdentifier);
        String identifierHash = OtpCryptoUtils.hashIdentifier(identifier);

        String otpKey = OtpRedisKeyBuilder.getOtpKey(purpose, identifierHash);
        String cooldownKey = OtpRedisKeyBuilder.getCooldownKey(purpose, identifierHash);
        String rateLimitKey = OtpRedisKeyBuilder.getRequestRateLimitKey(identifierHash);

        // 1. Check Resend Cooldown
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
            Long remainingCooldown = redisTemplate.getExpire(cooldownKey, TimeUnit.SECONDS);
            long cooldownSec = (remainingCooldown != null && remainingCooldown > 0) ? remainingCooldown : resendCooldownSeconds;
            log.debug("OTP request rejected: Cooldown active for identifierHash {}", identifierHash);
            throw new OtpCooldownException(cooldownSec);
        }

        // 2. Check Hourly Request Rate Limit
        Object rateLimitObj = redisTemplate.opsForValue().get(rateLimitKey);
        int currentCount = rateLimitObj != null ? Integer.parseInt(rateLimitObj.toString()) : 0;
        if (currentCount >= requestLimitPerHour) {
            log.debug("OTP request rejected: Hourly rate limit exceeded for identifierHash {}", identifierHash);
            throw new OtpRateLimitedException("Maximum OTP requests per hour exceeded for identifier");
        }

        // 3. Resolve User identity (if user exists)
        User user = findUserByIdentifier(identifierType, identifier).orElse(null);

        // 4. Generate Cryptographically Secure OTP and HMAC-SHA256 Hash
        String plaintextOtp = OtpCryptoUtils.generateSecureOtp(otpLength);
        String otpHash = OtpCryptoUtils.hashOtp(plaintextOtp, otpSecret);

        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(ttlSeconds);

        // 5. Create PostgreSQL Audit Record
        OtpRequest otpRequest = OtpRequest.builder()
                .user(user)
                .identifierType(identifierType)
                .identifier(identifier)
                .purpose(purpose)
                .status(OtpRequestStatus.REQUESTED)
                .attemptCount(0)
                .expiresAt(expiresAt)
                .build();

        OtpRequest savedRequest = otpRequestRepository.save(otpRequest);

        // 6. Store Active State Payload in Redis
        OtpRedisData redisData = OtpRedisData.builder()
                .requestId(savedRequest.getId())
                .otpHash(otpHash)
                .attemptCount(0)
                .createdAt(now)
                .expiresAt(expiresAt)
                .build();

        try {
            redisTemplate.opsForValue().set(otpKey, redisData, ttlSeconds, TimeUnit.SECONDS);

            // 7. Set Cooldown in Redis
            redisTemplate.opsForValue().set(cooldownKey, "1", resendCooldownSeconds, TimeUnit.SECONDS);

            // 8. Increment Hourly Rate Limit Counter
            Long newCount = redisTemplate.opsForValue().increment(rateLimitKey);
            if (newCount != null && newCount == 1) {
                redisTemplate.expire(rateLimitKey, 1, TimeUnit.HOURS);
            }
        } catch (Exception e) {
            log.error("Failed to persist OTP state to Redis for request ID {}", savedRequest.getId(), e);
            throw new IllegalStateException("Failed to issue OTP due to Redis storage error", e);
        }

        log.info("Issued OTP request ID {} for identifierHash {}", savedRequest.getId(), identifierHash);

        return OtpRequestResult.builder()
                .requestId(savedRequest.getId())
                .expiresInSeconds(ttlSeconds)
                .resendAfterSeconds(resendCooldownSeconds)
                .devExposedOtp(plaintextOtp)
                .build();
    }

    /**
     * Verifies a submitted OTP code against active Redis state using constant-time comparison.
     *
     * @param identifierType EMAIL or PHONE
     * @param rawIdentifier raw user input identifier string
     * @param purpose LOGIN, REGISTRATION, or ACCOUNT_RECOVERY
     * @param submittedOtp plaintext 6-digit OTP code submitted by user
     * @return OtpVerificationResult upon successful verification
     */
    @Transactional
    public OtpVerificationResult verifyOtp(AuthIdentifierType identifierType, String rawIdentifier, OtpPurpose purpose, String submittedOtp) {
        if (submittedOtp == null || submittedOtp.isBlank()) {
            throw new OtpInvalidException("Submitted OTP code cannot be empty");
        }

        String identifier = IdentifierNormalizer.normalize(identifierType, rawIdentifier);
        String identifierHash = OtpCryptoUtils.hashIdentifier(identifier);

        String otpKey = OtpRedisKeyBuilder.getOtpKey(purpose, identifierHash);

        // 1. Fetch active state from Redis
        Object redisObj = redisTemplate.opsForValue().get(otpKey);
        if (redisObj == null) {
            handleMissingRedisKey(identifier, purpose);
        }

        OtpRedisData redisData = convertToRedisData(redisObj);
        if (redisData == null) {
            throw new OtpExpiredException();
        }

        // 2. Check Expiration
        if (Instant.now().isAfter(redisData.getExpiresAt())) {
            redisTemplate.delete(otpKey);
            throw new OtpExpiredException();
        }

        // 3. Compute HMAC-SHA256 Hash of Submitted OTP & Perform Constant-Time Comparison
        String submittedHash = OtpCryptoUtils.hashOtp(submittedOtp.trim(), otpSecret);
        boolean matches = OtpCryptoUtils.constantTimeCompare(redisData.getOtpHash(), submittedHash);

        if (matches) {
            // SUCCESS: Single-use replay protection — Delete Redis OTP key immediately
            redisTemplate.delete(otpKey);

            // Update PostgreSQL Audit Record
            otpRequestRepository.findById(redisData.getRequestId()).ifPresent(req -> {
                req.setStatus(OtpRequestStatus.VERIFIED);
                req.setVerifiedAt(Instant.now());
                otpRequestRepository.save(req);
            });

            User user = findUserByIdentifier(identifierType, identifier).orElse(null);
            Long userId = user != null ? user.getId() : null;

            log.info("Successfully verified OTP request ID {} for identifierHash {}", redisData.getRequestId(), identifierHash);

            return OtpVerificationResult.builder()
                    .verified(true)
                    .userId(userId)
                    .identifier(identifier)
                    .purpose(purpose)
                    .build();
        } else {
            // FAILURE: Increment Attempt Count
            int newAttemptCount = redisData.getAttemptCount() + 1;

            // Update PostgreSQL Audit Record
            otpRequestRepository.findById(redisData.getRequestId()).ifPresent(req -> {
                req.setAttemptCount(newAttemptCount);
                if (newAttemptCount >= maxAttempts) {
                    req.setStatus(OtpRequestStatus.LOCKED);
                } else {
                    req.setStatus(OtpRequestStatus.FAILED);
                }
                otpRequestRepository.save(req);
            });

            if (newAttemptCount >= maxAttempts) {
                // Max attempts reached — Lock and delete Redis key
                redisTemplate.delete(otpKey);
                log.warn("OTP request ID {} locked: Max verification attempts ({}) exceeded", redisData.getRequestId(), maxAttempts);
                throw new OtpMaxAttemptsException();
            } else {
                // Update attempt count in Redis
                redisData.setAttemptCount(newAttemptCount);
                Long remainingTtl = redisTemplate.getExpire(otpKey, TimeUnit.SECONDS);
                if (remainingTtl != null && remainingTtl > 0) {
                    redisTemplate.opsForValue().set(otpKey, redisData, remainingTtl, TimeUnit.SECONDS);
                }

                int remainingAttempts = maxAttempts - newAttemptCount;
                log.debug("Invalid OTP submitted for request ID {}. Remaining attempts: {}", redisData.getRequestId(), remainingAttempts);
                throw new OtpInvalidException(remainingAttempts);
            }
        }
    }

    private void handleMissingRedisKey(String identifier, OtpPurpose purpose) {
        List<OtpRequest> requests = otpRequestRepository.findByIdentifier(identifier);
        Optional<OtpRequest> matching = requests.stream()
                .filter(r -> r.getPurpose() == purpose)
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .findFirst();

        if (matching.isPresent()) {
            OtpRequest req = matching.get();
            if (req.getStatus() == OtpRequestStatus.VERIFIED) {
                throw new OtpAlreadyUsedException();
            }
            if (req.getStatus() == OtpRequestStatus.LOCKED) {
                throw new OtpMaxAttemptsException();
            }
        }
        throw new OtpExpiredException();
    }

    private Optional<User> findUserByIdentifier(AuthIdentifierType type, String identifier) {
        return switch (type) {
            case EMAIL -> userRepository.findByEmail(identifier);
            case PHONE -> userRepository.findByPhone(identifier);
        };
    }

    private OtpRedisData convertToRedisData(Object obj) {
        if (obj instanceof OtpRedisData data) {
            return data;
        }
        if (obj != null) {
            return objectMapper.convertValue(obj, OtpRedisData.class);
        }
        return null;
    }
}
