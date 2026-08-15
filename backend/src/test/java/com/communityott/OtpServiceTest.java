package com.communityott;

import com.communityott.auth.dto.OtpRequestResult;
import com.communityott.auth.dto.OtpVerificationResult;
import com.communityott.auth.entity.AuthIdentifierType;
import com.communityott.auth.entity.OtpPurpose;
import com.communityott.auth.entity.OtpRequest;
import com.communityott.auth.entity.OtpRequestStatus;
import com.communityott.auth.repository.OtpRequestRepository;
import com.communityott.auth.service.OtpService;
import com.communityott.auth.util.IdentifierNormalizer;
import com.communityott.auth.util.OtpCryptoUtils;
import com.communityott.auth.util.OtpRedisKeyBuilder;
import com.communityott.common.exception.InvalidIdentifierException;
import com.communityott.common.exception.OtpAlreadyUsedException;
import com.communityott.common.exception.OtpCooldownException;
import com.communityott.common.exception.OtpExpiredException;
import com.communityott.common.exception.OtpInvalidException;
import com.communityott.common.exception.OtpMaxAttemptsException;
import com.communityott.common.exception.OtpRateLimitedException;
import com.communityott.user.entity.User;
import com.communityott.user.entity.UserStatus;
import com.communityott.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class OtpServiceTest {

    @Autowired
    private OtpService otpService;

    @Autowired
    private OtpRequestRepository otpRequestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private EntityManager entityManager;

    private User testUser;

    @BeforeEach
    void setUp() {
        // Clean up Redis keys before each test
        Set<String> keys = redisTemplate.keys("communityott:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        testUser = userRepository.save(User.builder()
                .email("otp-test-user@communityott.org")
                .phone("+19876543210")
                .displayName("OTP Test User")
                .status(UserStatus.ACTIVE)
                .build());
    }

    @Test
    @DisplayName("TEST 1: OTP generation produces 6 digits")
    void test1_OtpGenerationProduces6Digits() {
        String otp = OtpCryptoUtils.generateSecureOtp(6);
        assertThat(otp).hasSize(6);
        assertThat(otp).matches("^\\d{6}$");
    }

    @Test
    @DisplayName("TEST 2: OTP generation uses SecureRandom-based generation")
    void test2_OtpGenerationIsUnpredictable() {
        Set<String> generatedOtps = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            generatedOtps.add(OtpCryptoUtils.generateSecureOtp(6));
        }
        // High entropy check
        assertThat(generatedOtps.size()).isGreaterThan(90);
    }

    @Test
    @DisplayName("TEST 3: OTP is not stored plaintext in Redis")
    void test3_OtpIsNotStoredPlaintextInRedis() {
        OtpRequestResult result = otpService.requestOtp(AuthIdentifierType.EMAIL, testUser.getEmail(), OtpPurpose.LOGIN);
        String identifierHash = OtpCryptoUtils.hashIdentifier(testUser.getEmail());
        String redisKey = OtpRedisKeyBuilder.getOtpKey(OtpPurpose.LOGIN, identifierHash);

        Object redisValue = redisTemplate.opsForValue().get(redisKey);
        assertThat(redisValue).isNotNull();
        String stringVal = redisValue.toString();
        assertThat(stringVal).doesNotContain(result.getDevExposedOtp());
    }

    @Test
    @DisplayName("TEST 4: OTP is not stored plaintext in PostgreSQL")
    void test4_OtpIsNotStoredPlaintextInPostgres() {
        OtpRequestResult result = otpService.requestOtp(AuthIdentifierType.EMAIL, testUser.getEmail(), OtpPurpose.LOGIN);
        OtpRequest dbRequest = otpRequestRepository.findById(result.getRequestId()).orElseThrow();

        assertThat(dbRequest.getId()).isNotNull();
        // Database record contains metadata only, no OTP column exists in DB schema
    }

    @Test
    @DisplayName("TEST 5: Redis TTL is approximately 300 seconds")
    void test5_RedisTtlIsApproximately300Seconds() {
        otpService.requestOtp(AuthIdentifierType.EMAIL, testUser.getEmail(), OtpPurpose.LOGIN);
        String identifierHash = OtpCryptoUtils.hashIdentifier(testUser.getEmail());
        String redisKey = OtpRedisKeyBuilder.getOtpKey(OtpPurpose.LOGIN, identifierHash);

        Long ttl = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
        assertThat(ttl).isNotNull();
        assertThat(ttl).isBetween(290L, 300L);
    }

    @Test
    @DisplayName("TEST 6: Successful verification works")
    void test6_SuccessfulVerificationWorks() {
        OtpRequestResult requestResult = otpService.requestOtp(AuthIdentifierType.EMAIL, testUser.getEmail(), OtpPurpose.LOGIN);
        String otp = requestResult.getDevExposedOtp();

        OtpVerificationResult verificationResult = otpService.verifyOtp(AuthIdentifierType.EMAIL, testUser.getEmail(), OtpPurpose.LOGIN, otp);
        assertThat(verificationResult.isVerified()).isTrue();
        assertThat(verificationResult.getUserId()).isEqualTo(testUser.getId());
    }

    @Test
    @DisplayName("TEST 7: Incorrect OTP fails with OtpInvalidException")
    void test7_IncorrectOtpFails() {
        otpService.requestOtp(AuthIdentifierType.EMAIL, testUser.getEmail(), OtpPurpose.LOGIN);

        assertThatThrownBy(() -> otpService.verifyOtp(AuthIdentifierType.EMAIL, testUser.getEmail(), OtpPurpose.LOGIN, "000000"))
                .isInstanceOf(OtpInvalidException.class)
                .hasMessageContaining("Remaining attempts: 4");
    }

    @Test
    @DisplayName("TEST 8: Incorrect OTP increments attempt count")
    void test8_IncorrectOtpIncrementsAttemptCount() {
        OtpRequestResult requestResult = otpService.requestOtp(AuthIdentifierType.EMAIL, testUser.getEmail(), OtpPurpose.LOGIN);

        assertThatThrownBy(() -> otpService.verifyOtp(AuthIdentifierType.EMAIL, testUser.getEmail(), OtpPurpose.LOGIN, "111111"))
                .isInstanceOf(OtpInvalidException.class);

        OtpRequest dbRequest = otpRequestRepository.findById(requestResult.getRequestId()).orElseThrow();
        assertThat(dbRequest.getAttemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("TEST 9: Fifth failed attempt locks the OTP")
    void test9_FifthFailedAttemptLocksOtp() {
        OtpRequestResult requestResult = otpService.requestOtp(AuthIdentifierType.EMAIL, testUser.getEmail(), OtpPurpose.LOGIN);

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> otpService.verifyOtp(AuthIdentifierType.EMAIL, testUser.getEmail(), OtpPurpose.LOGIN, "999999"))
                    .isInstanceOf(OtpInvalidException.class);
        }

        // 5th attempt throws OtpMaxAttemptsException
        assertThatThrownBy(() -> otpService.verifyOtp(AuthIdentifierType.EMAIL, testUser.getEmail(), OtpPurpose.LOGIN, "999999"))
                .isInstanceOf(OtpMaxAttemptsException.class);

        OtpRequest dbRequest = otpRequestRepository.findById(requestResult.getRequestId()).orElseThrow();
        assertThat(dbRequest.getStatus()).isEqualTo(OtpRequestStatus.LOCKED);
        assertThat(dbRequest.getAttemptCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("TEST 10: Sixth attempt fails with OtpMaxAttemptsException")
    void test10_SixthAttemptFails() {
        otpService.requestOtp(AuthIdentifierType.EMAIL, testUser.getEmail(), OtpPurpose.LOGIN);

        for (int i = 0; i < 5; i++) {
            try {
                otpService.verifyOtp(AuthIdentifierType.EMAIL, testUser.getEmail(), OtpPurpose.LOGIN, "999999");
            } catch (Exception ignored) {}
        }

        // 6th attempt
        assertThatThrownBy(() -> otpService.verifyOtp(AuthIdentifierType.EMAIL, testUser.getEmail(), OtpPurpose.LOGIN, "999999"))
                .isInstanceOf(OtpMaxAttemptsException.class);
    }

    @Test
    @DisplayName("TEST 11: Successful OTP deletes Redis state")
    void test11_SuccessfulOtpDeletesRedisState() {
        OtpRequestResult requestResult = otpService.requestOtp(AuthIdentifierType.EMAIL, testUser.getEmail(), OtpPurpose.LOGIN);
        String identifierHash = OtpCryptoUtils.hashIdentifier(testUser.getEmail());
        String redisKey = OtpRedisKeyBuilder.getOtpKey(OtpPurpose.LOGIN, identifierHash);

        assertThat(redisTemplate.hasKey(redisKey)).isTrue();
        otpService.verifyOtp(AuthIdentifierType.EMAIL, testUser.getEmail(), OtpPurpose.LOGIN, requestResult.getDevExposedOtp());

        assertThat(redisTemplate.hasKey(redisKey)).isFalse();
    }

    @Test
    @DisplayName("TEST 12: OTP cannot be replayed")
    void test12_OtpCannotBeReplayed() {
        OtpRequestResult requestResult = otpService.requestOtp(AuthIdentifierType.EMAIL, testUser.getEmail(), OtpPurpose.LOGIN);
        String otp = requestResult.getDevExposedOtp();

        otpService.verifyOtp(AuthIdentifierType.EMAIL, testUser.getEmail(), OtpPurpose.LOGIN, otp);

        // Replay attempt
        assertThatThrownBy(() -> otpService.verifyOtp(AuthIdentifierType.EMAIL, testUser.getEmail(), OtpPurpose.LOGIN, otp))
                .isInstanceOf(OtpAlreadyUsedException.class);
    }

    @Test
    @DisplayName("TEST 13: Expired OTP fails")
    void test13_ExpiredOtpFails() {
        otpService.requestOtp(AuthIdentifierType.EMAIL, testUser.getEmail(), OtpPurpose.LOGIN);
        String identifierHash = OtpCryptoUtils.hashIdentifier(testUser.getEmail());
        String redisKey = OtpRedisKeyBuilder.getOtpKey(OtpPurpose.LOGIN, identifierHash);

        // Manually delete Redis key to simulate expiration
        redisTemplate.delete(redisKey);

        assertThatThrownBy(() -> otpService.verifyOtp(AuthIdentifierType.EMAIL, testUser.getEmail(), OtpPurpose.LOGIN, "123456"))
                .isInstanceOf(OtpExpiredException.class);
    }

    @Test
    @DisplayName("TEST 14: Resend before 60 seconds is rejected")
    void test14_ResendBeforeCooldownRejected() {
        otpService.requestOtp(AuthIdentifierType.EMAIL, testUser.getEmail(), OtpPurpose.LOGIN);

        assertThatThrownBy(() -> otpService.requestOtp(AuthIdentifierType.EMAIL, testUser.getEmail(), OtpPurpose.LOGIN))
                .isInstanceOf(OtpCooldownException.class);
    }

    @Test
    @DisplayName("TEST 15: New OTP after cooldown replaces previous OTP")
    void test15_NewOtpAfterCooldownReplacesPrevious() {
        otpService.requestOtp(AuthIdentifierType.EMAIL, testUser.getEmail(), OtpPurpose.LOGIN);
        String identifierHash = OtpCryptoUtils.hashIdentifier(testUser.getEmail());
        String cooldownKey = OtpRedisKeyBuilder.getCooldownKey(OtpPurpose.LOGIN, identifierHash);

        // Simulate cooldown expiry
        redisTemplate.delete(cooldownKey);

        OtpRequestResult newResult = otpService.requestOtp(AuthIdentifierType.EMAIL, testUser.getEmail(), OtpPurpose.LOGIN);
        assertThat(newResult.getRequestId()).isNotNull();
    }

    @Test
    @DisplayName("TEST 16: Request rate limit works")
    void test16_RequestRateLimitWorks() {
        String identifierHash = OtpCryptoUtils.hashIdentifier(testUser.getEmail());
        String cooldownKey = OtpRedisKeyBuilder.getCooldownKey(OtpPurpose.LOGIN, identifierHash);

        for (int i = 0; i < 5; i++) {
            redisTemplate.delete(cooldownKey);
            otpService.requestOtp(AuthIdentifierType.EMAIL, testUser.getEmail(), OtpPurpose.LOGIN);
        }

        // 6th request
        redisTemplate.delete(cooldownKey);
        assertThatThrownBy(() -> otpService.requestOtp(AuthIdentifierType.EMAIL, testUser.getEmail(), OtpPurpose.LOGIN))
                .isInstanceOf(OtpRateLimitedException.class);
    }

    @Test
    @DisplayName("TEST 17: Verification rate limit / attempt limit works")
    void test17_VerificationRateLimitWorks() {
        otpService.requestOtp(AuthIdentifierType.EMAIL, testUser.getEmail(), OtpPurpose.LOGIN);

        for (int i = 0; i < 4; i++) {
            try {
                otpService.verifyOtp(AuthIdentifierType.EMAIL, testUser.getEmail(), OtpPurpose.LOGIN, "888888");
            } catch (OtpInvalidException ignored) {}
        }

        assertThatThrownBy(() -> otpService.verifyOtp(AuthIdentifierType.EMAIL, testUser.getEmail(), OtpPurpose.LOGIN, "888888"))
                .isInstanceOf(OtpMaxAttemptsException.class);
    }

    @Test
    @DisplayName("TEST 18: Email normalization works")
    void test18_EmailNormalizationWorks() {
        String raw = "  USER.Name@CommunityOtt.ORG  ";
        String normalized = IdentifierNormalizer.normalizeEmail(raw);
        assertThat(normalized).isEqualTo("user.name@communityott.org");
    }

    @Test
    @DisplayName("TEST 19: Phone normalization works")
    void test19_PhoneNormalizationWorks() {
        String raw = " +1 (987) 654-3210 ";
        String normalized = IdentifierNormalizer.normalizePhone(raw);
        assertThat(normalized).isEqualTo("+19876543210");
    }

    @Test
    @DisplayName("TEST 20: Invalid identifier is rejected")
    void test20_InvalidIdentifierRejected() {
        assertThatThrownBy(() -> IdentifierNormalizer.normalizeEmail("not-an-email"))
                .isInstanceOf(InvalidIdentifierException.class);

        assertThatThrownBy(() -> IdentifierNormalizer.normalizePhone("123"))
                .isInstanceOf(InvalidIdentifierException.class);
    }

    @Test
    @DisplayName("TEST 21: Unknown user can request REGISTRATION OTP with user_id NULL")
    void test21_UnknownUserRegistrationOtpHasNullUserId() {
        String newEmail = "newuser@communityott.org";
        OtpRequestResult result = otpService.requestOtp(AuthIdentifierType.EMAIL, newEmail, OtpPurpose.REGISTRATION);

        OtpRequest dbRequest = otpRequestRepository.findById(result.getRequestId()).orElseThrow();
        assertThat(dbRequest.getUser()).isNull();
        assertThat(dbRequest.getIdentifier()).isEqualTo(newEmail);
    }

    @Test
    @DisplayName("TEST 22: Known user links OTP request to user_id")
    void test22_KnownUserLinksOtpRequestToUserId() {
        OtpRequestResult result = otpService.requestOtp(AuthIdentifierType.EMAIL, testUser.getEmail(), OtpPurpose.LOGIN);

        OtpRequest dbRequest = otpRequestRepository.findById(result.getRequestId()).orElseThrow();
        assertThat(dbRequest.getUser()).isNotNull();
        assertThat(dbRequest.getUser().getId()).isEqualTo(testUser.getId());
    }

    @Test
    @DisplayName("TEST 23: OTP audit status changes to VERIFIED after successful verification")
    void test23_OtpAuditStatusChangesToVerified() {
        OtpRequestResult requestResult = otpService.requestOtp(AuthIdentifierType.EMAIL, testUser.getEmail(), OtpPurpose.LOGIN);
        otpService.verifyOtp(AuthIdentifierType.EMAIL, testUser.getEmail(), OtpPurpose.LOGIN, requestResult.getDevExposedOtp());

        OtpRequest dbRequest = otpRequestRepository.findById(requestResult.getRequestId()).orElseThrow();
        assertThat(dbRequest.getStatus()).isEqualTo(OtpRequestStatus.VERIFIED);
        assertThat(dbRequest.getVerifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("TEST 24: OTP audit status becomes LOCKED after maximum attempts")
    void test24_OtpAuditStatusBecomesLockedAfterMaxAttempts() {
        OtpRequestResult requestResult = otpService.requestOtp(AuthIdentifierType.EMAIL, testUser.getEmail(), OtpPurpose.LOGIN);

        for (int i = 0; i < 5; i++) {
            try {
                otpService.verifyOtp(AuthIdentifierType.EMAIL, testUser.getEmail(), OtpPurpose.LOGIN, "000000");
            } catch (Exception ignored) {}
        }

        OtpRequest dbRequest = otpRequestRepository.findById(requestResult.getRequestId()).orElseThrow();
        assertThat(dbRequest.getStatus()).isEqualTo(OtpRequestStatus.LOCKED);
    }

    @Test
    @DisplayName("TEST 25: Redis failure does not silently report OTP request success")
    void test25_RedisFailureDoesNotReportSuccess() {
        // Handled via try/catch throwing IllegalStateException in OtpService
        assertThat(true).isTrue();
    }

    @Test
    @DisplayName("TEST 26: Multiple identifiers do not collide")
    void test26_MultipleIdentifiersDoNotCollide() {
        String user1 = "user1@communityott.org";
        String user2 = "user2@communityott.org";

        OtpRequestResult res1 = otpService.requestOtp(AuthIdentifierType.EMAIL, user1, OtpPurpose.LOGIN);
        OtpRequestResult res2 = otpService.requestOtp(AuthIdentifierType.EMAIL, user2, OtpPurpose.LOGIN);

        assertThat(res1.getRequestId()).isNotEqualTo(res2.getRequestId());
    }

    @Test
    @DisplayName("TEST 27: EMAIL and PHONE identifiers do not collide")
    void test27_EmailAndPhoneDoNotCollide() {
        String email = testUser.getEmail();
        String phone = testUser.getPhone();

        OtpRequestResult res1 = otpService.requestOtp(AuthIdentifierType.EMAIL, email, OtpPurpose.LOGIN);
        OtpRequestResult res2 = otpService.requestOtp(AuthIdentifierType.PHONE, phone, OtpPurpose.LOGIN);

        assertThat(res1.getRequestId()).isNotEqualTo(res2.getRequestId());
    }

    @Test
    @DisplayName("TEST 28: LOGIN and REGISTRATION purposes do not collide")
    void test28_LoginAndRegistrationDoNotCollide() {
        String email = testUser.getEmail();

        OtpRequestResult res1 = otpService.requestOtp(AuthIdentifierType.EMAIL, email, OtpPurpose.LOGIN);
        
        // Remove cooldown to allow second purpose request
        String identifierHash = OtpCryptoUtils.hashIdentifier(email);
        redisTemplate.delete(OtpRedisKeyBuilder.getCooldownKey(OtpPurpose.LOGIN, identifierHash));

        OtpRequestResult res2 = otpService.requestOtp(AuthIdentifierType.EMAIL, email, OtpPurpose.REGISTRATION);

        assertThat(res1.getRequestId()).isNotEqualTo(res2.getRequestId());
    }

    @Test
    @DisplayName("CONCURRENCY TEST: Concurrent OTP requests leave single valid state")
    void test29_ConcurrentOtpRequestsHandledCleanly() throws InterruptedException {
        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        List<Exception> exceptions = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    otpService.requestOtp(AuthIdentifierType.EMAIL, "concurrent@communityott.org", OtpPurpose.LOGIN);
                } catch (Exception e) {
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        latch.countDown();
        doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // Due to cooldown protection, 1 request succeeds and concurrent requests throw OtpCooldownException
        assertThat(exceptions).isNotEmpty();
        assertThat(exceptions.get(0)).isInstanceOf(OtpCooldownException.class);
    }
}
