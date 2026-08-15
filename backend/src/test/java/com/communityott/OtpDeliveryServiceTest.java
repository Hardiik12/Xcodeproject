package com.communityott;

import com.communityott.auth.delivery.DevelopmentEmailOtpDeliveryProvider;
import com.communityott.auth.delivery.DevelopmentSmsOtpDeliveryProvider;
import com.communityott.auth.delivery.OtpDeliveryProvider;
import com.communityott.auth.delivery.OtpDeliveryService;
import com.communityott.auth.dto.OtpDeliveryResult;
import com.communityott.auth.dto.OtpRequestResult;
import com.communityott.auth.dto.OtpVerificationResult;
import com.communityott.auth.entity.AuthIdentifierType;
import com.communityott.auth.entity.OtpDeliveryAttempt;
import com.communityott.auth.entity.OtpDeliveryStatus;
import com.communityott.auth.entity.OtpPurpose;
import com.communityott.auth.entity.OtpRequest;
import com.communityott.auth.entity.OtpRequestStatus;
import com.communityott.auth.repository.OtpDeliveryAttemptRepository;
import com.communityott.auth.repository.OtpRequestRepository;
import com.communityott.auth.service.OtpService;
import com.communityott.auth.util.OtpCryptoUtils;
import com.communityott.auth.util.OtpRedisKeyBuilder;
import com.communityott.common.exception.OtpDeliveryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive integration tests for Phase 4.3 OTP Delivery Architecture.
 */
@SpringBootTest
@ActiveProfiles("local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OtpDeliveryServiceTest {

    @Autowired
    private OtpDeliveryService otpDeliveryService;

    @Autowired
    private OtpService otpService;

    @Autowired
    private DevelopmentEmailOtpDeliveryProvider emailProvider;

    @Autowired
    private DevelopmentSmsOtpDeliveryProvider smsProvider;

    @Autowired
    private OtpDeliveryAttemptRepository deliveryAttemptRepository;

    @Autowired
    private OtpRequestRepository otpRequestRepository;

    @Autowired
    private com.communityott.user.repository.UserRepository userRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void setUp() {
        emailProvider.clearTestStore();
        smsProvider.clearTestStore();
    }

    @Test
    @Order(1)
    @DisplayName("TEST 1: Email identifier routes to Email delivery provider")
    void test1_EmailIdentifierRoutesToEmailProvider() {
        OtpRequest req = otpRequestRepository.save(OtpRequest.builder()
                .identifierType(AuthIdentifierType.EMAIL)
                .identifier("user@example.com")
                .purpose(OtpPurpose.LOGIN)
                .status(OtpRequestStatus.REQUESTED)
                .attemptCount(0)
                .expiresAt(Instant.now().plusSeconds(300))
                .build());

        OtpDeliveryResult result = otpDeliveryService.deliverOtp(
                AuthIdentifierType.EMAIL,
                "user@example.com",
                "123456",
                OtpPurpose.LOGIN,
                req.getId()
        );

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getChannel()).isEqualTo(AuthIdentifierType.EMAIL);
        assertThat(result.getProvider()).isEqualTo(DevelopmentEmailOtpDeliveryProvider.PROVIDER_NAME);
        assertThat(result.getDeliveryId()).startsWith("dev-email-");
        assertThat(emailProvider.getLastDeliveredOtp("user@example.com")).isEqualTo("123456");
    }

    @Test
    @Order(2)
    @DisplayName("TEST 2: Phone identifier routes to SMS delivery provider")
    void test2_PhoneIdentifierRoutesToSmsProvider() {
        OtpRequest req = otpRequestRepository.save(OtpRequest.builder()
                .identifierType(AuthIdentifierType.PHONE)
                .identifier("+15551234567")
                .purpose(OtpPurpose.REGISTRATION)
                .status(OtpRequestStatus.REQUESTED)
                .attemptCount(0)
                .expiresAt(Instant.now().plusSeconds(300))
                .build());

        OtpDeliveryResult result = otpDeliveryService.deliverOtp(
                AuthIdentifierType.PHONE,
                "+15551234567",
                "654321",
                OtpPurpose.REGISTRATION,
                req.getId()
        );

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getChannel()).isEqualTo(AuthIdentifierType.PHONE);
        assertThat(result.getProvider()).isEqualTo(DevelopmentSmsOtpDeliveryProvider.PROVIDER_NAME);
        assertThat(result.getDeliveryId()).startsWith("dev-sms-");
        assertThat(smsProvider.getLastDeliveredOtp("+15551234567")).isEqualTo("654321");
    }

    @Test
    @Order(3)
    @DisplayName("TEST 3: Delivery attempt creates SENT audit record in PostgreSQL")
    void test3_DeliveryAttemptCreatesSentAuditRecord() {
        OtpRequest req = otpRequestRepository.save(OtpRequest.builder()
                .identifierType(AuthIdentifierType.EMAIL)
                .identifier("audit@example.com")
                .purpose(OtpPurpose.ACCOUNT_RECOVERY)
                .status(OtpRequestStatus.REQUESTED)
                .attemptCount(0)
                .expiresAt(Instant.now().plusSeconds(300))
                .build());

        OtpDeliveryResult result = otpDeliveryService.deliverOtp(
                AuthIdentifierType.EMAIL,
                "audit@example.com",
                "987654",
                OtpPurpose.ACCOUNT_RECOVERY,
                req.getId()
        );

        List<OtpDeliveryAttempt> attempts = deliveryAttemptRepository.findByOtpRequestId(req.getId());
        assertThat(attempts).hasSize(1);

        OtpDeliveryAttempt attempt = attempts.get(0);
        assertThat(attempt.getStatus()).isEqualTo(OtpDeliveryStatus.SENT);
        assertThat(attempt.getChannel()).isEqualTo(AuthIdentifierType.EMAIL);
        assertThat(attempt.getProvider()).isEqualTo(DevelopmentEmailOtpDeliveryProvider.PROVIDER_NAME);
        assertThat(attempt.getProviderMessageId()).isEqualTo(result.getDeliveryId());
        assertThat(attempt.getDeliveredAt()).isNotNull();
        assertThat(attempt.getCreatedAt()).isNotNull();
    }

    @Test
    @Order(4)
    @DisplayName("TEST 4: Plaintext OTP is NOT present in OtpDeliveryResult")
    void test4_OtpNotPresentInDeliveryResult() {
        OtpRequest req = otpRequestRepository.save(OtpRequest.builder()
                .identifierType(AuthIdentifierType.EMAIL)
                .identifier("safe@example.com")
                .purpose(OtpPurpose.LOGIN)
                .status(OtpRequestStatus.REQUESTED)
                .attemptCount(0)
                .expiresAt(Instant.now().plusSeconds(300))
                .build());

        OtpDeliveryResult result = otpDeliveryService.deliverOtp(
                AuthIdentifierType.EMAIL,
                "safe@example.com",
                "112233",
                OtpPurpose.LOGIN,
                req.getId()
        );

        assertThat(result.toString()).doesNotContain("112233");
    }

    @Test
    @Order(5)
    @DisplayName("TEST 5: Non-existent request ID throws OtpDeliveryException")
    void test5_NonExistentRequestIdThrowsOtpDeliveryException() {
        assertThatThrownBy(() -> otpDeliveryService.deliverOtp(
                AuthIdentifierType.EMAIL,
                "ghost@example.com",
                "112233",
                OtpPurpose.LOGIN,
                999999L
        )).isInstanceOf(OtpDeliveryException.class);
    }

    @Test
    @Order(6)
    @DisplayName("TEST 6: Custom failing provider creates FAILED audit record and throws OtpDeliveryException")
    void test6_ProviderFailureCreatesFailedAuditRecord() {
        OtpDeliveryProvider failingProvider = new OtpDeliveryProvider() {
            @Override
            public boolean supports(AuthIdentifierType type) {
                return type == AuthIdentifierType.EMAIL;
            }

            @Override
            public String getProviderName() {
                return "MOCK_FAILING_PROVIDER";
            }

            @Override
            public OtpDeliveryResult send(String identifier, String otp, OtpPurpose purpose) {
                throw new RuntimeException("Simulated vendor API timeout");
            }
        };

        OtpDeliveryService customDeliveryService = new OtpDeliveryService(
                List.of(failingProvider),
                deliveryAttemptRepository,
                otpRequestRepository
        );

        OtpRequest req = otpRequestRepository.save(OtpRequest.builder()
                .identifierType(AuthIdentifierType.EMAIL)
                .identifier("fail@example.com")
                .purpose(OtpPurpose.LOGIN)
                .status(OtpRequestStatus.REQUESTED)
                .attemptCount(0)
                .expiresAt(Instant.now().plusSeconds(300))
                .build());

        assertThatThrownBy(() -> customDeliveryService.deliverOtp(
                AuthIdentifierType.EMAIL,
                "fail@example.com",
                "999999",
                OtpPurpose.LOGIN,
                req.getId()
        )).isInstanceOf(OtpDeliveryException.class);

        List<OtpDeliveryAttempt> attempts = deliveryAttemptRepository.findByOtpRequestId(req.getId());
        assertThat(attempts).hasSize(1);
        assertThat(attempts.get(0).getStatus()).isEqualTo(OtpDeliveryStatus.FAILED);
        assertThat(attempts.get(0).getFailureCode()).isEqualTo("RuntimeException");
    }

    @Test
    @Order(7)
    @DisplayName("TEST 7: OtpService requestOtp dispatches delivery and verifies successfully")
    void test7_OtpServiceEndToEndWithDelivery() {
        OtpRequestResult requestResult = otpService.requestOtp(
                AuthIdentifierType.EMAIL,
                "e2e@communityott.org",
                OtpPurpose.LOGIN
        );

        assertThat(requestResult.getRequestId()).isNotNull();
        String deliveredOtp = emailProvider.getLastDeliveredOtp("e2e@communityott.org");
        assertThat(deliveredOtp).isNotNull().matches("^\\d{6}$");

        // Verify with the delivered OTP
        OtpVerificationResult verifyResult = otpService.verifyOtp(
                AuthIdentifierType.EMAIL,
                "e2e@communityott.org",
                OtpPurpose.LOGIN,
                deliveredOtp
        );

        assertThat(verifyResult.isVerified()).isTrue();
        assertThat(verifyResult.getIdentifier()).isEqualTo("e2e@communityott.org");
    }

    @Test
    @Order(8)
    @DisplayName("TEST 8: Delivery failure rolls back Redis state and marks PostgreSQL request as FAILED")
    void test8_DeliveryFailureRollbackCompensation() {
        OtpDeliveryProvider failingProvider = new OtpDeliveryProvider() {
            @Override
            public boolean supports(AuthIdentifierType type) {
                return true;
            }

            @Override
            public String getProviderName() {
                return "FAILING_PROVIDER";
            }

            @Override
            public OtpDeliveryResult send(String identifier, String otp, OtpPurpose purpose) {
                throw new RuntimeException("Simulated gateway failure");
            }
        };

        OtpDeliveryService failingDeliveryService = new OtpDeliveryService(
                List.of(failingProvider),
                deliveryAttemptRepository,
                otpRequestRepository
        );

        OtpService serviceWithFailingDelivery = new OtpService(
                otpRequestRepository,
                userRepository,
                redisTemplate,
                null,
                failingDeliveryService
        );
        org.springframework.test.util.ReflectionTestUtils.setField(serviceWithFailingDelivery, "otpSecret", "test_secret_32bytes_long_key_communityott");
        org.springframework.test.util.ReflectionTestUtils.setField(serviceWithFailingDelivery, "otpLength", 6);
        org.springframework.test.util.ReflectionTestUtils.setField(serviceWithFailingDelivery, "ttlSeconds", 300L);
        org.springframework.test.util.ReflectionTestUtils.setField(serviceWithFailingDelivery, "maxAttempts", 5);
        org.springframework.test.util.ReflectionTestUtils.setField(serviceWithFailingDelivery, "resendCooldownSeconds", 60L);
        org.springframework.test.util.ReflectionTestUtils.setField(serviceWithFailingDelivery, "requestLimitPerHour", 5);

        String identifier = "rollback@communityott.org";
        String identifierHash = OtpCryptoUtils.hashIdentifier(identifier);
        String otpKey = OtpRedisKeyBuilder.getOtpKey(OtpPurpose.LOGIN, identifierHash);
        String cooldownKey = OtpRedisKeyBuilder.getCooldownKey(OtpPurpose.LOGIN, identifierHash);

        assertThatThrownBy(() -> serviceWithFailingDelivery.requestOtp(
                AuthIdentifierType.EMAIL,
                identifier,
                OtpPurpose.LOGIN
        )).isInstanceOf(OtpDeliveryException.class);

        // Redis active key and cooldown must be rolled back / deleted
        assertThat(redisTemplate.hasKey(otpKey)).isFalse();
        assertThat(redisTemplate.hasKey(cooldownKey)).isFalse();
    }
}
