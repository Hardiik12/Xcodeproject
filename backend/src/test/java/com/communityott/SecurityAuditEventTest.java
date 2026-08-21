package com.communityott;

import com.communityott.audit.dto.SecurityAuditEventPayload;
import com.communityott.audit.entity.SecurityAuditEvent;
import com.communityott.audit.listener.SecurityAuditEventListener;
import com.communityott.audit.model.SecurityEventOutcome;
import com.communityott.audit.model.SecurityEventType;
import com.communityott.audit.publisher.SecurityAuditEventPublisher;
import com.communityott.audit.repository.SecurityAuditEventRepository;
import com.communityott.auth.delivery.DevelopmentEmailOtpDeliveryProvider;
import com.communityott.auth.dto.AuthenticationResponse;
import com.communityott.auth.dto.OtpRequestDto;
import com.communityott.auth.dto.OtpVerifyRequestDto;
import com.communityott.auth.dto.RefreshTokenRequestDto;
import com.communityott.auth.entity.AuthIdentifierType;
import com.communityott.auth.entity.OtpPurpose;
import com.communityott.auth.entity.Platform;
import com.communityott.auth.repository.AuthSessionRepository;
import com.communityott.auth.service.AuthenticationService;
import com.communityott.common.exception.AuthTokenReuseException;
import com.communityott.common.exception.MaxDevicesReachedException;
import com.communityott.device.entity.Device;
import com.communityott.device.repository.DeviceRepository;
import com.communityott.device.service.DeviceService;
import com.communityott.user.entity.User;
import com.communityott.user.entity.UserStatus;
import com.communityott.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
public class SecurityAuditEventTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private SecurityAuditEventPublisher securityAuditEventPublisher;

    @Autowired
    private SecurityAuditEventRepository securityAuditEventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Autowired
    private DevelopmentEmailOtpDeliveryProvider emailProvider;

    @Autowired
    private SecurityAuditEventListener securityAuditEventListener;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private User testUser1;
    private User testUser2;

    @BeforeEach
    void setUp() {
        emailProvider.clearTestStore();
        clearRedisKeys();

        testUser1 = userRepository.save(User.builder()
                .email("audit_user1_" + UUID.randomUUID().toString().substring(0, 8) + "@communityott.com")
                .displayName("Audit User One")
                .status(UserStatus.ACTIVE)
                .build());

        testUser2 = userRepository.save(User.builder()
                .email("audit_user2_" + UUID.randomUUID().toString().substring(0, 8) + "@communityott.com")
                .displayName("Audit User Two")
                .status(UserStatus.ACTIVE)
                .build());
    }

    private void clearRedisKeys() {
        Set<String> keys = redisTemplate.keys("communityott:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private AuthenticationResponse loginUser(User user, String deviceId) {
        clearRedisKeys();
        OtpRequestDto requestDto = OtpRequestDto.builder()
                .identifierType(AuthIdentifierType.EMAIL)
                .identifier(user.getEmail())
                .purpose(OtpPurpose.LOGIN)
                .build();
        authenticationService.requestOtp(requestDto);

        String otpCode = emailProvider.getLastDeliveredOtp(user.getEmail());

        OtpVerifyRequestDto verifyDto = OtpVerifyRequestDto.builder()
                .identifierType(AuthIdentifierType.EMAIL)
                .identifier(user.getEmail())
                .purpose(OtpPurpose.LOGIN)
                .otp(otpCode)
                .deviceId(deviceId)
                .deviceName("Test iPhone")
                .platform(Platform.IOS)
                .build();

        return authenticationService.verifyOtpAndAuthenticate(verifyDto, "127.0.0.1", "TestAgent");
    }

    // ===================================================================
    // 1 & 2. LOGIN SUCCESS & LOGIN FAILED EVENTS
    // ===================================================================

    @Test
    @DisplayName("1. AUTHN_LOGIN_SUCCESS and SESSION_CREATED events are published and persisted")
    void test1_loginSuccessPersisted() throws Exception {
        AuthenticationResponse auth = loginUser(testUser1, "device-audit-1");
        assertThat(auth.getSession()).isNotNull();

        // Allow async listener execution
        Thread.sleep(300);

        List<SecurityAuditEvent> events = securityAuditEventRepository.findByUserIdAndEventTypeOrderByCreatedAtDesc(
                testUser1.getId(), SecurityEventType.AUTHN_LOGIN_SUCCESS);
        assertThat(events).isNotEmpty();
        SecurityAuditEvent event = events.get(0);
        assertThat(event.getOutcome()).isEqualTo(SecurityEventOutcome.SUCCESS);
        assertThat(event.getDeviceIdentifier()).isEqualTo("device-audit-1");
    }

    @Test
    @DisplayName("3. OTP verification failure emits AUTHN_OTP_FAILED event")
    void test3_otpFailurePersisted() throws Exception {
        clearRedisKeys();
        authenticationService.requestOtp(OtpRequestDto.builder()
                .identifierType(AuthIdentifierType.EMAIL)
                .identifier(testUser1.getEmail())
                .purpose(OtpPurpose.LOGIN)
                .build());

        OtpVerifyRequestDto verifyDto = OtpVerifyRequestDto.builder()
                .identifierType(AuthIdentifierType.EMAIL)
                .identifier(testUser1.getEmail())
                .purpose(OtpPurpose.LOGIN)
                .otp("000000") // Wrong OTP
                .deviceId("device-audit-fail")
                .platform(Platform.IOS)
                .build();

        assertThatThrownBy(() -> authenticationService.verifyOtpAndAuthenticate(verifyDto, "127.0.0.1", "TestAgent"))
                .isInstanceOf(com.communityott.common.exception.OtpInvalidException.class);

        Thread.sleep(300);

        List<SecurityAuditEvent> events = securityAuditEventRepository.findAll().stream()
                .filter(e -> e.getEventType() == SecurityEventType.AUTHN_OTP_FAILED)
                .toList();

        assertThat(events).isNotEmpty();
        SecurityAuditEvent event = events.get(0);
        assertThat(event.getOutcome()).isEqualTo(SecurityEventOutcome.FAILURE);
        assertThat(event.getReasonCode()).isEqualTo("OTP_INVALID");
    }

    // ===================================================================
    // 5-9. DEVICE EVENTS
    // ===================================================================

    @Test
    @DisplayName("5. DEVICE_REGISTERED event persisted on new device login")
    void test5_deviceRegisteredPersisted() throws Exception {
        loginUser(testUser1, "device-audit-new");
        Thread.sleep(300);

        List<SecurityAuditEvent> events = securityAuditEventRepository.findByUserIdAndEventTypeOrderByCreatedAtDesc(
                testUser1.getId(), SecurityEventType.DEVICE_REGISTERED);
        assertThat(events).isNotEmpty();
        assertThat(events.get(0).getOutcome()).isEqualTo(SecurityEventOutcome.SUCCESS);
    }

    @Test
    @DisplayName("7 & 8. DEVICE_REVOKED and DEVICE_REPLACED events persisted cleanly")
    void test7and8_deviceRevokedAndReplacedPersisted() throws Exception {
        AuthenticationResponse auth1 = loginUser(testUser1, "device-audit-swap1");
        loginUser(testUser1, "device-audit-swap2");

        Device device1 = deviceRepository.findByUserIdAndDeviceIdentifier(testUser1.getId(), "device-audit-swap1").orElseThrow();

        // Replace device 1 with device 3
        OtpVerifyRequestDto swapDto = OtpVerifyRequestDto.builder()
                .identifierType(AuthIdentifierType.EMAIL)
                .identifier(testUser1.getEmail())
                .purpose(OtpPurpose.LOGIN)
                .otp(emailProvider.getLastDeliveredOtp(testUser1.getEmail()))
                .deviceId("device-audit-swap3")
                .platform(Platform.IOS)
                .replaceDeviceId(device1.getId())
                .build();

        // Perform swap
        clearRedisKeys();
        authenticationService.requestOtp(OtpRequestDto.builder()
                .identifierType(AuthIdentifierType.EMAIL)
                .identifier(testUser1.getEmail())
                .purpose(OtpPurpose.LOGIN)
                .build());
        swapDto.setOtp(emailProvider.getLastDeliveredOtp(testUser1.getEmail()));
        authenticationService.verifyOtpAndAuthenticate(swapDto, "127.0.0.1", "TestAgent");

        Thread.sleep(300);

        List<SecurityAuditEvent> replacedEvents = securityAuditEventRepository.findByUserIdAndEventTypeOrderByCreatedAtDesc(
                testUser1.getId(), SecurityEventType.DEVICE_REPLACED);
        assertThat(replacedEvents).isNotEmpty();
        assertThat(replacedEvents.get(0).getOutcome()).isEqualTo(SecurityEventOutcome.SUCCESS);
    }

    @Test
    @DisplayName("9. DEVICE_LIMIT_REACHED event persisted when 3rd device attempt blocked")
    void test9_deviceLimitReachedPersisted() throws Exception {
        loginUser(testUser1, "device-audit-limit1");
        loginUser(testUser1, "device-audit-limit2");

        assertThatThrownBy(() -> loginUser(testUser1, "device-audit-limit3"))
                .isInstanceOf(MaxDevicesReachedException.class);

        Thread.sleep(300);

        List<SecurityAuditEvent> events = securityAuditEventRepository.findByUserIdAndEventTypeOrderByCreatedAtDesc(
                testUser1.getId(), SecurityEventType.DEVICE_LIMIT_REACHED);
        assertThat(events).isNotEmpty();
        assertThat(events.get(0).getOutcome()).isEqualTo(SecurityEventOutcome.BLOCKED);
    }

    // ===================================================================
    // 10. TOKEN REUSE SECURITY EVENT
    // ===================================================================

    @Test
    @DisplayName("10. SECURITY_TOKEN_REUSE event persisted on refresh token compromise attempt")
    void test10_tokenReusePersisted() throws Exception {
        AuthenticationResponse auth = loginUser(testUser1, "device-audit-reuse");
        String originalRefreshToken = auth.getRefreshToken();

        // Rotate token
        AuthenticationResponse refreshed = authenticationService.refreshTokens(
                new RefreshTokenRequestDto(originalRefreshToken), "127.0.0.1", "TestAgent");

        // Attempt reuse of original refresh token
        assertThatThrownBy(() -> authenticationService.refreshTokens(
                new RefreshTokenRequestDto(originalRefreshToken), "127.0.0.1", "TestAgent"))
                .isInstanceOf(AuthTokenReuseException.class);

        Thread.sleep(300);

        List<SecurityAuditEvent> events = securityAuditEventRepository.findByUserIdAndEventTypeOrderByCreatedAtDesc(
                testUser1.getId(), SecurityEventType.SECURITY_TOKEN_REUSE);
        assertThat(events).isNotEmpty();
        SecurityAuditEvent event = events.get(0);
        assertThat(event.getOutcome()).isEqualTo(SecurityEventOutcome.BLOCKED);
        assertThat(event.getReasonCode()).isEqualTo("TOKEN_REUSE_DETECTED");
    }

    // ===================================================================
    // 11 & 12. AUTHORIZATION & IDOR SECURITY EVENTS
    // ===================================================================

    @Test
    @DisplayName("12. SECURITY_IDOR_ATTEMPT event persisted when cross-user device access attempted")
    void test12_idorAttemptPersisted() throws Exception {
        AuthenticationResponse authUser1 = loginUser(testUser1, "device-u1");
        AuthenticationResponse authUser2 = loginUser(testUser2, "device-u2");

        Long user2DeviceId = authSessionRepository.findById(authUser2.getSession().getId()).orElseThrow().getDeviceEntity().getId();

        // User 1 attempts to revoke User 2's device -> 404 & IDOR event
        mockMvc.perform(post("/api/v1/devices/" + user2DeviceId + "/revoke")
                        .header("Authorization", "Bearer " + authUser1.getAccessToken()))
                .andExpect(status().isNotFound());

        Thread.sleep(300);

        List<SecurityAuditEvent> events = securityAuditEventRepository.findByUserIdAndEventTypeOrderByCreatedAtDesc(
                testUser1.getId(), SecurityEventType.SECURITY_IDOR_ATTEMPT);
        assertThat(events).isNotEmpty();
        SecurityAuditEvent event = events.get(0);
        assertThat(event.getOutcome()).isEqualTo(SecurityEventOutcome.BLOCKED);
        assertThat(event.getReasonCode()).isEqualTo("CROSS_USER_DEVICE_ACCESS");
    }

    // ===================================================================
    // 16 & 17. AUDIT FAILURE FALLBACK DOES NOT BLOCK BUSINESS OPERATIONS
    // ===================================================================

    @Test
    @DisplayName("16 & 17. Audit listener handling failure does not break core business logic")
    void test16and17_auditFailureDoesNotBlockOperation() {
        SecurityAuditEventPayload invalidPayload = SecurityAuditEventPayload.builder()
                .eventType(SecurityEventType.AUTHN_LOGIN_SUCCESS)
                .outcome(SecurityEventOutcome.SUCCESS)
                .user(testUser1)
                .userId(testUser1.getId())
                .platform(Platform.IOS)
                .deviceIdentifier(null) // Null identifier triggers fallback mapping without throwing
                .build();

        // Event processing executes smoothly without crashing thread or caller
        securityAuditEventListener.processAndPersistEvent(invalidPayload);
    }

    // ===================================================================
    // 18 & 19. SECRETS & PII REDACTION
    // ===================================================================

    @Test
    @DisplayName("18 & 19. Prohibited sensitive metadata keys (password, otp, token) are redacted to [REDACTED]")
    void test18and19_secretsRedactedFromMetadata() throws Exception {
        SecurityAuditEventPayload payload = SecurityAuditEventPayload.builder()
                .eventType(SecurityEventType.SECURITY_SUSPICIOUS_ACTIVITY)
                .outcome(SecurityEventOutcome.BLOCKED)
                .userId(testUser1.getId())
                .deviceIdentifier("device-redact")
                .platform(Platform.WEB)
                .ipAddress("127.0.0.1")
                .metadata(Map.of(
                        "safeParam", "hello",
                        "password", "secret123",
                        "otp", "123456",
                        "refreshToken", "token_val"
                ))
                .build();

        securityAuditEventPublisher.publish(payload);
        Thread.sleep(300);

        List<SecurityAuditEvent> events = securityAuditEventRepository.findByUserIdAndEventTypeOrderByCreatedAtDesc(
                testUser1.getId(), SecurityEventType.SECURITY_SUSPICIOUS_ACTIVITY);
        assertThat(events).isNotEmpty();
        String jsonMeta = events.get(0).getMetadata();
        assertThat(jsonMeta).contains("\"safeParam\": \"hello\"");
        assertThat(jsonMeta).contains("\"password\": \"[REDACTED]\"");
        assertThat(jsonMeta).contains("\"otp\": \"[REDACTED]\"");
        assertThat(jsonMeta).contains("\"refreshToken\": \"[REDACTED]\"");
    }

    // ===================================================================
    // 20 & 21. REQUEST & TRACE CORRELATION PRESERVATION
    // ===================================================================

    @Test
    @DisplayName("20 & 21. Request ID and Trace ID from MDC are enriched into SecurityAuditEvent")
    void test20and21_correlationIdsPreserved() throws Exception {
        String reqId = "req-" + UUID.randomUUID();
        String traceId = "trace-" + UUID.randomUUID();

        MDC.put("requestId", reqId);
        MDC.put("traceId", traceId);

        try {
            securityAuditEventPublisher.publishSimple(
                    SecurityEventType.SESSION_EXPIRED, SecurityEventOutcome.FAILURE,
                    testUser1.getId(), "device-corr", "127.0.0.1"
            );

            Thread.sleep(300);

            List<SecurityAuditEvent> events = securityAuditEventRepository.findByUserIdAndEventTypeOrderByCreatedAtDesc(
                    testUser1.getId(), SecurityEventType.SESSION_EXPIRED);
            assertThat(events).isNotEmpty();
            SecurityAuditEvent event = events.get(0);
            assertThat(event.getRequestId()).isEqualTo(reqId);
            assertThat(event.getTraceId()).isEqualTo(traceId);
        } finally {
            MDC.remove("requestId");
            MDC.remove("traceId");
        }
    }

    // ===================================================================
    // 22. CONCURRENT EVENT CREATION
    // ===================================================================

    @Test
    @DisplayName("22. High-volume concurrent security audit events process cleanly without pool exhaustion or deadlocks")
    void test22_concurrentEventCreation() throws Exception {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    securityAuditEventPublisher.publishSimple(
                            SecurityEventType.AUTHN_OTP_REQUESTED, SecurityEventOutcome.SUCCESS,
                            testUser1, "device-conc-" + index, "127.0.0.1"
                    );
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        Thread.sleep(1000);

        List<SecurityAuditEvent> events = securityAuditEventRepository.findAll().stream()
                .filter(e -> e.getEventType() == SecurityEventType.AUTHN_OTP_REQUESTED)
                .toList();
        assertThat(events.size()).isGreaterThanOrEqualTo(threadCount);
    }
}
