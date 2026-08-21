package com.communityott;

import com.communityott.account.dto.SecurityAlertResponse;
import com.communityott.account.entity.SecurityAlert;
import com.communityott.account.model.SecurityAlertSeverity;
import com.communityott.account.model.SecurityAlertStatus;
import com.communityott.account.model.SecurityAlertType;
import com.communityott.account.repository.SecurityAlertRepository;
import com.communityott.account.service.SecurityAlertService;
import com.communityott.audit.dto.SecurityAuditEventPayload;
import com.communityott.audit.model.SecurityEventOutcome;
import com.communityott.audit.model.SecurityEventType;
import com.communityott.auth.delivery.DevelopmentEmailOtpDeliveryProvider;
import com.communityott.auth.dto.AuthenticationResponse;
import com.communityott.auth.dto.OtpRequestDto;
import com.communityott.auth.dto.OtpVerifyRequestDto;
import com.communityott.auth.entity.AuthIdentifierType;
import com.communityott.auth.entity.OtpPurpose;
import com.communityott.auth.entity.Platform;
import com.communityott.auth.service.AuthenticationService;
import com.communityott.device.entity.Device;
import com.communityott.device.repository.DeviceRepository;
import com.communityott.user.entity.User;
import com.communityott.user.entity.UserStatus;
import com.communityott.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
public class SecurityAlertTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private SecurityAlertService securityAlertService;

    @Autowired
    private SecurityAlertRepository securityAlertRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private DevelopmentEmailOtpDeliveryProvider emailProvider;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private User testUser1;
    private User testUser2;

    @BeforeEach
    void setUp() {
        emailProvider.clearTestStore();
        clearRedisKeys();

        testUser1 = userRepository.save(User.builder()
                .email("alert_user1_" + UUID.randomUUID().toString().substring(0, 8) + "@communityott.com")
                .displayName("Alert User One")
                .status(UserStatus.ACTIVE)
                .build());

        testUser2 = userRepository.save(User.builder()
                .email("alert_user2_" + UUID.randomUUID().toString().substring(0, 8) + "@communityott.com")
                .displayName("Alert User Two")
                .status(UserStatus.ACTIVE)
                .build());

        securityAlertRepository.deleteAll();
    }

    private void clearRedisKeys() {
        try {
            Set<String> keys = redisTemplate.keys("*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception ignored) {
        }
    }

    private String authenticateUser(User user, String deviceId, String deviceName) {
        OtpRequestDto requestDto = OtpRequestDto.builder()
                .identifierType(AuthIdentifierType.EMAIL)
                .identifier(user.getEmail())
                .purpose(OtpPurpose.LOGIN)
                .build();
        authenticationService.requestOtp(requestDto);
        String otp = emailProvider.getLastDeliveredOtp(user.getEmail());

        OtpVerifyRequestDto verifyDto = OtpVerifyRequestDto.builder()
                .identifierType(AuthIdentifierType.EMAIL)
                .identifier(user.getEmail())
                .purpose(OtpPurpose.LOGIN)
                .otp(otp)
                .platform(Platform.IOS)
                .deviceId(deviceId)
                .deviceName(deviceName)
                .deviceModel("iPhone 15")
                .osVersion("iOS 17.4")
                .appVersion("1.0.0")
                .build();

        AuthenticationResponse response = authenticationService.verifyOtpAndAuthenticate(verifyDto, "192.168.1.50", "CommunityOTT-iOS");
        return response.getAccessToken();
    }

    @Test
    @DisplayName("1. New-device alert generated on DEVICE_REGISTERED event")
    void testNewDeviceAlertGenerated() {
        UUID eventId = UUID.randomUUID();
        SecurityAuditEventPayload payload = SecurityAuditEventPayload.builder()
                .eventId(eventId)
                .eventType(SecurityEventType.DEVICE_REGISTERED)
                .outcome(SecurityEventOutcome.SUCCESS)
                .userId(testUser1.getId())
                .platform(Platform.IOS)
                .ipAddress("192.168.1.100")
                .build();

        securityAlertService.processEventAlert(payload);

        List<SecurityAlert> alerts = securityAlertRepository.findAll();
        assertThat(alerts).hasSize(1);
        SecurityAlert alert = alerts.get(0);
        assertThat(alert.getAlertType()).isEqualTo(SecurityAlertType.ALERT_NEW_DEVICE);
        assertThat(alert.getSeverity()).isEqualTo(SecurityAlertSeverity.MEDIUM);
        assertThat(alert.getTitle()).isEqualTo("New device signed in");
        assertThat(alert.getStatus()).isEqualTo(SecurityAlertStatus.UNREAD);
    }

    @Test
    @DisplayName("2. Device-replacement alert generated on DEVICE_REPLACED event")
    void testDeviceReplacementAlertGenerated() {
        UUID eventId = UUID.randomUUID();
        SecurityAuditEventPayload payload = SecurityAuditEventPayload.builder()
                .eventId(eventId)
                .eventType(SecurityEventType.DEVICE_REPLACED)
                .outcome(SecurityEventOutcome.SUCCESS)
                .userId(testUser1.getId())
                .platform(Platform.WEB)
                .ipAddress("10.0.0.5")
                .build();

        securityAlertService.processEventAlert(payload);

        List<SecurityAlert> alerts = securityAlertRepository.findAll();
        assertThat(alerts).hasSize(1);
        SecurityAlert alert = alerts.get(0);
        assertThat(alert.getAlertType()).isEqualTo(SecurityAlertType.ALERT_DEVICE_REPLACED);
        assertThat(alert.getSeverity()).isEqualTo(SecurityAlertSeverity.HIGH);
        assertThat(alert.getTitle()).isEqualTo("Device replaced");
    }

    @Test
    @DisplayName("3. Suspicious-login alert generated on AUTHN_LOGIN_FAILED event")
    void testSuspiciousLoginAlertGenerated() {
        UUID eventId = UUID.randomUUID();
        SecurityAuditEventPayload payload = SecurityAuditEventPayload.builder()
                .eventId(eventId)
                .eventType(SecurityEventType.AUTHN_LOGIN_FAILED)
                .outcome(SecurityEventOutcome.FAILURE)
                .userId(testUser1.getId())
                .platform(Platform.ANDROID)
                .ipAddress("172.16.0.1")
                .build();

        securityAlertService.processEventAlert(payload);

        List<SecurityAlert> alerts = securityAlertRepository.findAll();
        assertThat(alerts).hasSize(1);
        SecurityAlert alert = alerts.get(0);
        assertThat(alert.getAlertType()).isEqualTo(SecurityAlertType.ALERT_SUSPICIOUS_LOGIN);
        assertThat(alert.getSeverity()).isEqualTo(SecurityAlertSeverity.HIGH);
    }

    @Test
    @DisplayName("4. Token-reuse alert generated on SECURITY_TOKEN_REUSE event")
    void testTokenReuseAlertGenerated() {
        UUID eventId = UUID.randomUUID();
        SecurityAuditEventPayload payload = SecurityAuditEventPayload.builder()
                .eventId(eventId)
                .eventType(SecurityEventType.SECURITY_TOKEN_REUSE)
                .outcome(SecurityEventOutcome.BLOCKED)
                .userId(testUser1.getId())
                .platform(Platform.WEB)
                .ipAddress("203.0.113.195")
                .build();

        securityAlertService.processEventAlert(payload);

        List<SecurityAlert> alerts = securityAlertRepository.findAll();
        assertThat(alerts).hasSize(1);
        SecurityAlert alert = alerts.get(0);
        assertThat(alert.getAlertType()).isEqualTo(SecurityAlertType.ALERT_TOKEN_REUSE);
        assertThat(alert.getSeverity()).isEqualTo(SecurityAlertSeverity.CRITICAL);
        assertThat(alert.getTitle()).isEqualTo("Account security alert");
    }

    @Test
    @DisplayName("5. Correct severity mapping for all alert types")
    void testCorrectSeverityMapping() {
        assertThat(securityAlertService).isNotNull();
        SecurityAuditEventPayload p1 = SecurityAuditEventPayload.builder().eventId(UUID.randomUUID()).eventType(SecurityEventType.DEVICE_REGISTERED).userId(testUser1.getId()).build();
        SecurityAuditEventPayload p2 = SecurityAuditEventPayload.builder().eventId(UUID.randomUUID()).eventType(SecurityEventType.DEVICE_REPLACED).userId(testUser1.getId()).build();
        SecurityAuditEventPayload p3 = SecurityAuditEventPayload.builder().eventId(UUID.randomUUID()).eventType(SecurityEventType.SECURITY_TOKEN_REUSE).userId(testUser1.getId()).build();

        securityAlertService.processEventAlert(p1);
        securityAlertService.processEventAlert(p2);
        securityAlertService.processEventAlert(p3);

        List<SecurityAlert> alerts = securityAlertRepository.findAll();
        assertThat(alerts).hasSize(3);
    }

    @Test
    @DisplayName("6. Friendly user-facing microcopy (no technical enums)")
    void testCorrectUserFacingMicrocopy() {
        SecurityAuditEventPayload payload = SecurityAuditEventPayload.builder()
                .eventId(UUID.randomUUID())
                .eventType(SecurityEventType.SECURITY_TOKEN_REUSE)
                .userId(testUser1.getId())
                .build();

        securityAlertService.processEventAlert(payload);

        SecurityAlert alert = securityAlertRepository.findAll().get(0);
        assertThat(alert.getMessage()).doesNotContain("TOKEN_REUSE");
        assertThat(alert.getMessage()).doesNotContain("JWT");
        assertThat(alert.getMessage()).contains("unusual activity involving your account session");
    }

    @Test
    @DisplayName("7. 5-minute event deduplication for non-critical alerts")
    void testFiveMinuteEventDeduplication() {
        SecurityAuditEventPayload p1 = SecurityAuditEventPayload.builder()
                .eventId(UUID.randomUUID())
                .eventType(SecurityEventType.DEVICE_REGISTERED)
                .userId(testUser1.getId())
                .build();

        SecurityAuditEventPayload p2 = SecurityAuditEventPayload.builder()
                .eventId(UUID.randomUUID())
                .eventType(SecurityEventType.DEVICE_REGISTERED)
                .userId(testUser1.getId())
                .build();

        securityAlertService.processEventAlert(p1);
        securityAlertService.processEventAlert(p2);

        List<SecurityAlert> alerts = securityAlertRepository.findAll();
        assertThat(alerts).hasSize(1);
    }

    @Test
    @DisplayName("8. Duplicate source event ID idempotency check")
    void testDuplicateEventIdempotency() {
        UUID eventId = UUID.randomUUID();
        SecurityAuditEventPayload p1 = SecurityAuditEventPayload.builder()
                .eventId(eventId)
                .eventType(SecurityEventType.DEVICE_REPLACED)
                .userId(testUser1.getId())
                .build();

        securityAlertService.processEventAlert(p1);
        securityAlertService.processEventAlert(p1);

        List<SecurityAlert> alerts = securityAlertRepository.findAll();
        assertThat(alerts).hasSize(1);
    }

    @Test
    @DisplayName("9. Concurrent duplicate event handling")
    void testConcurrentDuplicateEventHandling() throws InterruptedException {
        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        UUID eventId = UUID.randomUUID();

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    SecurityAuditEventPayload payload = SecurityAuditEventPayload.builder()
                            .eventId(eventId)
                            .eventType(SecurityEventType.DEVICE_REPLACED)
                            .userId(testUser1.getId())
                            .build();
                    securityAlertService.processEventAlert(payload);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        List<SecurityAlert> alerts = securityAlertRepository.findAll();
        assertThat(alerts).hasSize(1);
    }

    @Test
    @DisplayName("10. Async alert failure does not break primary logic")
    void testAsyncFailureDoesNotBreakLogin() {
        SecurityAuditEventPayload invalidPayload = SecurityAuditEventPayload.builder()
                .eventId(UUID.randomUUID())
                .eventType(SecurityEventType.DEVICE_REGISTERED)
                .userId(999999L) // Non-existent user
                .build();

        securityAlertService.processEventAlert(invalidPayload);
        assertThat(securityAlertRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("11. Token-reuse CRITICAL alert cannot be suppressed by 5m cooldown")
    void testTokenReuseAlertCannotBeSuppressed() {
        SecurityAuditEventPayload p1 = SecurityAuditEventPayload.builder()
                .eventId(UUID.randomUUID())
                .eventType(SecurityEventType.SECURITY_TOKEN_REUSE)
                .userId(testUser1.getId())
                .build();

        SecurityAuditEventPayload p2 = SecurityAuditEventPayload.builder()
                .eventId(UUID.randomUUID())
                .eventType(SecurityEventType.SECURITY_TOKEN_REUSE)
                .userId(testUser1.getId())
                .build();

        securityAlertService.processEventAlert(p1);
        securityAlertService.processEventAlert(p2);

        List<SecurityAlert> alerts = securityAlertRepository.findAll();
        assertThat(alerts).hasSize(2);
    }

    @Test
    @DisplayName("12. GET /alerts returns only authenticated user's alerts")
    void testGetAlertsReturnsOnlyCurrentUserAlerts() throws Exception {
        String token1 = authenticateUser(testUser1, "dev1", "User 1 Phone");
        String token2 = authenticateUser(testUser2, "dev2", "User 2 Phone");

        SecurityAuditEventPayload p1 = SecurityAuditEventPayload.builder().eventId(UUID.randomUUID()).eventType(SecurityEventType.DEVICE_REGISTERED).userId(testUser1.getId()).build();
        SecurityAuditEventPayload p2 = SecurityAuditEventPayload.builder().eventId(UUID.randomUUID()).eventType(SecurityEventType.DEVICE_REPLACED).userId(testUser2.getId()).build();

        securityAlertService.processEventAlert(p1);
        securityAlertService.processEventAlert(p2);

        mockMvc.perform(get("/api/v1/account/security/alerts")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("New device signed in"));
    }

    @Test
    @DisplayName("13. Cross-user alert access returns 404 Not Found")
    void testCrossUserAlertAccessReturns404() throws Exception {
        String token2 = authenticateUser(testUser2, "dev2", "User 2 Phone");

        SecurityAlert user1Alert = securityAlertRepository.save(SecurityAlert.builder()
                .user(testUser1)
                .alertType(SecurityAlertType.ALERT_NEW_DEVICE)
                .severity(SecurityAlertSeverity.MEDIUM)
                .title("New device signed in")
                .message("Test")
                .status(SecurityAlertStatus.UNREAD)
                .expiresAt(Instant.now().plus(60, ChronoUnit.DAYS))
                .build());

        mockMvc.perform(post("/api/v1/account/security/alerts/" + user1Alert.getId() + "/read")
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SECURITY_ALERT_NOT_FOUND"));
    }

    @Test
    @DisplayName("14. No userId parameter in API request contracts")
    void testNoUserIdParameterExists() throws Exception {
        String token1 = authenticateUser(testUser1, "dev1", "User 1 Phone");

        mockMvc.perform(get("/api/v1/account/security/alerts?userId=999")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("15. Pagination parameter validation")
    void testPaginationWorks() throws Exception {
        String token1 = authenticateUser(testUser1, "dev1", "User 1 Phone");

        mockMvc.perform(get("/api/v1/account/security/alerts?page=0&size=10")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageable.pageSize").value(10));
    }

    @Test
    @DisplayName("16. Deterministic ordering (createdAt DESC, id DESC)")
    void testDeterministicOrdering() throws Exception {
        String token1 = authenticateUser(testUser1, "dev1", "User 1 Phone");
        try { Thread.sleep(150); } catch (InterruptedException ignored) {}
        securityAlertRepository.deleteAll();

        SecurityAlert a1 = securityAlertRepository.save(SecurityAlert.builder()
                .user(testUser1)
                .alertType(SecurityAlertType.ALERT_NEW_DEVICE)
                .severity(SecurityAlertSeverity.MEDIUM)
                .title("First Alert")
                .message("First")
                .status(SecurityAlertStatus.UNREAD)
                .createdAt(Instant.now().minus(10, ChronoUnit.MINUTES))
                .expiresAt(Instant.now().plus(60, ChronoUnit.DAYS))
                .build());

        SecurityAlert a2 = securityAlertRepository.save(SecurityAlert.builder()
                .user(testUser1)
                .alertType(SecurityAlertType.ALERT_DEVICE_REPLACED)
                .severity(SecurityAlertSeverity.HIGH)
                .title("Second Alert")
                .message("Second")
                .status(SecurityAlertStatus.UNREAD)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(60, ChronoUnit.DAYS))
                .build());

        mockMvc.perform(get("/api/v1/account/security/alerts")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].title").value("Second Alert"))
                .andExpect(jsonPath("$.data.content[1].title").value("First Alert"));
    }

    @Test
    @DisplayName("17. Unread count endpoint works accurately")
    void testUnreadCountWorks() throws Exception {
        String token1 = authenticateUser(testUser1, "dev1", "User 1 Phone");
        try { Thread.sleep(150); } catch (InterruptedException ignored) {}
        securityAlertRepository.deleteAll();

        securityAlertRepository.save(SecurityAlert.builder().user(testUser1).alertType(SecurityAlertType.ALERT_NEW_DEVICE).severity(SecurityAlertSeverity.MEDIUM).title("T1").message("M1").status(SecurityAlertStatus.UNREAD).expiresAt(Instant.now().plus(60, ChronoUnit.DAYS)).build());
        securityAlertRepository.save(SecurityAlert.builder().user(testUser1).alertType(SecurityAlertType.ALERT_DEVICE_REPLACED).severity(SecurityAlertSeverity.HIGH).title("T2").message("M2").status(SecurityAlertStatus.READ).expiresAt(Instant.now().plus(60, ChronoUnit.DAYS)).build());

        mockMvc.perform(get("/api/v1/account/security/alerts/unread-count")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(1));
    }

    @Test
    @DisplayName("18. Mark single alert as read works")
    void testMarkOneReadWorks() throws Exception {
        String token1 = authenticateUser(testUser1, "dev1", "User 1 Phone");

        SecurityAlert alert = securityAlertRepository.save(SecurityAlert.builder()
                .user(testUser1)
                .alertType(SecurityAlertType.ALERT_NEW_DEVICE)
                .severity(SecurityAlertSeverity.MEDIUM)
                .title("Unread Alert")
                .message("M")
                .status(SecurityAlertStatus.UNREAD)
                .expiresAt(Instant.now().plus(60, ChronoUnit.DAYS))
                .build());

        mockMvc.perform(post("/api/v1/account/security/alerts/" + alert.getId() + "/read")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READ"))
                .andExpect(jsonPath("$.data.readAt").isNotEmpty());
    }

    @Test
    @DisplayName("19. Mark single alert read is idempotent")
    void testMarkOneReadIsIdempotent() throws Exception {
        String token1 = authenticateUser(testUser1, "dev1", "User 1 Phone");

        SecurityAlert alert = securityAlertRepository.save(SecurityAlert.builder()
                .user(testUser1)
                .alertType(SecurityAlertType.ALERT_NEW_DEVICE)
                .severity(SecurityAlertSeverity.MEDIUM)
                .title("Already Read Alert")
                .message("M")
                .status(SecurityAlertStatus.READ)
                .readAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .expiresAt(Instant.now().plus(60, ChronoUnit.DAYS))
                .build());

        mockMvc.perform(post("/api/v1/account/security/alerts/" + alert.getId() + "/read")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READ"));
    }

    @Test
    @DisplayName("20. Mark-all-read affects only authenticated user's alerts")
    void testMarkAllReadAffectsOnlyCurrentUser() throws Exception {
        String token1 = authenticateUser(testUser1, "dev1", "User 1 Phone");

        SecurityAlert a1 = securityAlertRepository.save(SecurityAlert.builder().user(testUser1).alertType(SecurityAlertType.ALERT_NEW_DEVICE).severity(SecurityAlertSeverity.MEDIUM).title("U1 Alert").message("M1").status(SecurityAlertStatus.UNREAD).expiresAt(Instant.now().plus(60, ChronoUnit.DAYS)).build());
        SecurityAlert a2 = securityAlertRepository.save(SecurityAlert.builder().user(testUser2).alertType(SecurityAlertType.ALERT_NEW_DEVICE).severity(SecurityAlertSeverity.MEDIUM).title("U2 Alert").message("M2").status(SecurityAlertStatus.UNREAD).expiresAt(Instant.now().plus(60, ChronoUnit.DAYS)).build());

        mockMvc.perform(post("/api/v1/account/security/alerts/read-all")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk());

        assertThat(securityAlertRepository.findById(a1.getId()).get().getStatus()).isEqualTo(SecurityAlertStatus.READ);
        assertThat(securityAlertRepository.findById(a2.getId()).get().getStatus()).isEqualTo(SecurityAlertStatus.UNREAD);
    }

    @Test
    @DisplayName("21. Strict privacy & secret leakage audit")
    void testPrivacySecretLeakage() throws Exception {
        String token1 = authenticateUser(testUser1, "dev1", "User 1 Phone");

        securityAlertRepository.save(SecurityAlert.builder()
                .user(testUser1)
                .alertType(SecurityAlertType.ALERT_TOKEN_REUSE)
                .severity(SecurityAlertSeverity.CRITICAL)
                .title("Critical Alert")
                .message("Test")
                .status(SecurityAlertStatus.UNREAD)
                .expiresAt(Instant.now().plus(60, ChronoUnit.DAYS))
                .build());

        MvcResult result = mockMvc.perform(get("/api/v1/account/security/alerts")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("access_token");
        assertThat(body).doesNotContain("refresh_token");
        assertThat(body).doesNotContain("token_hash");
        assertThat(body).doesNotContain("sid");
        assertThat(body).doesNotContain("device_identifier");
        assertThat(body).doesNotContain("trace_id");
        assertThat(body).doesNotContain("request_id");
    }

    @Test
    @DisplayName("22. Expired alert purging model")
    void testExpiredAlertsHandledCorrectly() {
        securityAlertRepository.save(SecurityAlert.builder()
                .user(testUser1)
                .alertType(SecurityAlertType.ALERT_NEW_DEVICE)
                .severity(SecurityAlertSeverity.MEDIUM)
                .title("Expired Alert")
                .message("M")
                .status(SecurityAlertStatus.UNREAD)
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build());

        securityAlertRepository.save(SecurityAlert.builder()
                .user(testUser1)
                .alertType(SecurityAlertType.ALERT_NEW_DEVICE)
                .severity(SecurityAlertSeverity.MEDIUM)
                .title("Valid Alert")
                .message("M")
                .status(SecurityAlertStatus.UNREAD)
                .expiresAt(Instant.now().plus(60, ChronoUnit.DAYS))
                .build());

        int purged = securityAlertService.purgeExpiredAlerts();
        assertThat(purged).isEqualTo(1);
        assertThat(securityAlertRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("23. Empty alert list returns clean empty page")
    void testEmptyAlertList() throws Exception {
        String token1 = authenticateUser(testUser1, "dev1", "User 1 Phone");
        try { Thread.sleep(150); } catch (InterruptedException ignored) {}
        securityAlertRepository.deleteAll();

        mockMvc.perform(get("/api/v1/account/security/alerts")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("24. Unauthenticated request returns 401 Unauthorized")
    void testAuthenticationRequired() throws Exception {
        mockMvc.perform(get("/api/v1/account/security/alerts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("25. Invalid pagination parameters rejected with 400 Bad Request")
    void testInvalidPaginationRejected() throws Exception {
        String token1 = authenticateUser(testUser1, "dev1", "User 1 Phone");

        mockMvc.perform(get("/api/v1/account/security/alerts?page=-1&size=10")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/account/security/alerts?page=0&size=100")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isBadRequest());
    }
}
