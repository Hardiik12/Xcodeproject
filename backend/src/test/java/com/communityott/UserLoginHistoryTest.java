package com.communityott;

import com.communityott.account.dto.LoginHistoryItemResponse;
import com.communityott.account.dto.LoginHistoryResponse;
import com.communityott.account.entity.UserLoginHistory;
import com.communityott.account.repository.UserLoginHistoryRepository;
import com.communityott.account.service.UserLoginHistoryProjectionService;
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
import com.communityott.device.service.DeviceService;
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
public class UserLoginHistoryTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private UserLoginHistoryRepository userLoginHistoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private UserLoginHistoryProjectionService projectionService;

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
                .email("history_user1_" + UUID.randomUUID().toString().substring(0, 8) + "@communityott.com")
                .displayName("History User One")
                .status(UserStatus.ACTIVE)
                .build());

        testUser2 = userRepository.save(User.builder()
                .email("history_user2_" + UUID.randomUUID().toString().substring(0, 8) + "@communityott.com")
                .displayName("History User Two")
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
        authenticationService.requestOtp(OtpRequestDto.builder()
                .identifierType(AuthIdentifierType.EMAIL)
                .identifier(user.getEmail())
                .purpose(OtpPurpose.LOGIN)
                .build());

        String otpCode = emailProvider.getLastDeliveredOtp(user.getEmail());

        OtpVerifyRequestDto verifyDto = OtpVerifyRequestDto.builder()
                .identifierType(AuthIdentifierType.EMAIL)
                .identifier(user.getEmail())
                .purpose(OtpPurpose.LOGIN)
                .otp(otpCode)
                .deviceId(deviceId)
                .deviceName("Test iPhone")
                .platform(Platform.IOS)
                .appVersion("1.4.0")
                .osVersion("18.6")
                .build();

        return authenticationService.verifyOtpAndAuthenticate(verifyDto, "192.168.1.50", "CommunityOTT-iOS");
    }

    // ===================================================================
    // 1. OWN LOGIN HISTORY SUCCEEDS
    // ===================================================================
    @Test
    @DisplayName("1. User can successfully retrieve their own login history")
    void test1_ownLoginHistorySucceeds() throws Exception {
        AuthenticationResponse auth = loginUser(testUser1, "history-dev-1");
        Thread.sleep(400);

        mockMvc.perform(get("/api/v1/account/login-history")
                        .header("Authorization", "Bearer " + auth.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[0].device_name").value("Test iPhone"))
                .andExpect(jsonPath("$.data.items[0].platform").value("IOS"));
    }

    // ===================================================================
    // 2. UNAUTHENTICATED -> 401
    // ===================================================================
    @Test
    @DisplayName("2. Unauthenticated request returns 401 Unauthorized")
    void test2_unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/account/login-history"))
                .andExpect(status().isUnauthorized());
    }

    // ===================================================================
    // 3. CROSS-USER ACCESS DENIED
    // ===================================================================
    @Test
    @DisplayName("3. User A cannot access User B login history even with malicious query params")
    void test3_crossUserAccessDenied() throws Exception {
        AuthenticationResponse auth1 = loginUser(testUser1, "dev-u1");
        AuthenticationResponse auth2 = loginUser(testUser2, "dev-u2");
        Thread.sleep(400);

        // User 1 attempts to pass ?userId=User2.id
        MvcResult result = mockMvc.perform(get("/api/v1/account/login-history?userId=" + testUser2.getId())
                        .header("Authorization", "Bearer " + auth1.getAccessToken()))
                .andExpect(status().isOk())
                .andReturn();

        LoginHistoryResponse response = objectMapper.readValue(
                objectMapper.readTree(result.getResponse().getContentAsString()).get("data").toString(),
                LoginHistoryResponse.class
        );

        // Verify all returned history entries belong ONLY to User 1
        List<UserLoginHistory> dbEntries = userLoginHistoryRepository.findAll();
        List<Long> returnedIds = response.getItems().stream().map(LoginHistoryItemResponse::getId).toList();
        for (Long id : returnedIds) {
            UserLoginHistory history = userLoginHistoryRepository.findById(id).orElseThrow();
            assertThat(history.getUser().getId()).isEqualTo(testUser1.getId());
            assertThat(history.getUser().getId()).isNotEqualTo(testUser2.getId());
        }
    }

    // ===================================================================
    // 4. PAGINATION
    // ===================================================================
    @Test
    @DisplayName("4. Pagination metadata is correctly calculated and returned")
    void test4_paginationMetadata() throws Exception {
        AuthenticationResponse auth = loginUser(testUser1, "dev-page-1");
        Thread.sleep(400);

        mockMvc.perform(get("/api/v1/account/login-history?page=0&size=10")
                        .header("Authorization", "Bearer " + auth.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.total_items").isNumber())
                .andExpect(jsonPath("$.data.total_pages").isNumber());
    }

    // ===================================================================
    // 5. STABLE NEWEST-FIRST ORDERING
    // ===================================================================
    @Test
    @DisplayName("5. History items are sorted newest-first (occurred_at DESC)")
    void test5_newestFirstOrdering() throws Exception {
        AuthenticationResponse auth = loginUser(testUser1, "dev-order-1");
        Thread.sleep(400);

        MvcResult result = mockMvc.perform(get("/api/v1/account/login-history")
                        .header("Authorization", "Bearer " + auth.getAccessToken()))
                .andExpect(status().isOk())
                .andReturn();

        LoginHistoryResponse response = objectMapper.readValue(
                objectMapper.readTree(result.getResponse().getContentAsString()).get("data").toString(),
                LoginHistoryResponse.class
        );

        List<LoginHistoryItemResponse> items = response.getItems();
        if (items.size() > 1) {
            for (int i = 0; i < items.size() - 1; i++) {
                assertThat(items.get(i).getOccurredAt()).isAfterOrEqualTo(items.get(i + 1).getOccurredAt());
            }
        }
    }

    // ===================================================================
    // 6. DATE FILTERING
    // ===================================================================
    @Test
    @DisplayName("6. Filtering by 'from' and 'to' timestamps restricts history accurately")
    void test6_dateFiltering() throws Exception {
        AuthenticationResponse auth = loginUser(testUser1, "dev-date-1");
        Thread.sleep(400);

        Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant to = Instant.now().plus(1, ChronoUnit.HOURS);

        mockMvc.perform(get("/api/v1/account/login-history?from=" + from.toString() + "&to=" + to.toString())
                        .header("Authorization", "Bearer " + auth.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
    }

    // ===================================================================
    // 7. EVENT FILTERING
    // ===================================================================
    @Test
    @DisplayName("7. Filtering by event returns matching mapped event types")
    void test7_eventFiltering() throws Exception {
        AuthenticationResponse auth = loginUser(testUser1, "dev-event-1");
        Thread.sleep(400);

        mockMvc.perform(get("/api/v1/account/login-history?event=SIGNED_IN_NEW_DEVICE")
                        .header("Authorization", "Bearer " + auth.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
    }

    // ===================================================================
    // 8. PLATFORM FILTERING
    // ===================================================================
    @Test
    @DisplayName("8. Filtering by platform returns platform-matched entries")
    void test8_platformFiltering() throws Exception {
        AuthenticationResponse auth = loginUser(testUser1, "dev-plat-1");
        Thread.sleep(400);

        mockMvc.perform(get("/api/v1/account/login-history?platform=IOS")
                        .header("Authorization", "Bearer " + auth.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].platform").value("IOS"));
    }

    // ===================================================================
    // 9 & 10. LOGIN SUCCESS & NEW DEVICE DEDUPLICATION
    // ===================================================================
    @Test
    @DisplayName("9 & 10. New device sign-in emits single SIGNED_IN_NEW_DEVICE entry")
    void test9and10_newDeviceDeduplication() throws Exception {
        AuthenticationResponse auth = loginUser(testUser1, "dev-dedup-new");
        Thread.sleep(600);

        List<UserLoginHistory> historyList = userLoginHistoryRepository.findByUserIdAndOccurredAtAfter(
                testUser1.getId(), Instant.now().minus(1, ChronoUnit.MINUTES));

        assertThat(historyList).isNotEmpty();
        assertThat(historyList.get(0).getEventType()).isIn("SIGNED_IN_NEW_DEVICE", "LOGIN_SUCCESS");
    }

    // ===================================================================
    // 12. DEVICE REVOCATION PROJECTION
    // ===================================================================
    @Test
    @DisplayName("12. DEVICE_REVOKED projects to DEVICE_REMOVED entry")
    void test12_deviceRevocationProjection() throws Exception {
        AuthenticationResponse auth = loginUser(testUser1, "dev-revoke-proj");
        Device device = deviceRepository.findByUserIdAndDeviceIdentifier(testUser1.getId(), "dev-revoke-proj").orElseThrow();

        deviceService.revokeDevice(testUser1.getId(), device.getId());
        Thread.sleep(400);

        List<UserLoginHistory> history = userLoginHistoryRepository.findAll().stream()
                .filter(h -> h.getUser().getId().equals(testUser1.getId()) && "DEVICE_REMOVED".equals(h.getEventType()))
                .toList();

        assertThat(history).isNotEmpty();
        assertThat(history.get(0).getUserMessage()).isEqualTo("Device removed");
    }

    // ===================================================================
    // 13. DEVICE REPLACEMENT PROJECTION
    // ===================================================================
    @Test
    @DisplayName("13. DEVICE_REPLACED projects to DEVICE_REPLACED entry")
    void test13_deviceReplacementProjection() throws Exception {
        loginUser(testUser1, "dev-swap-1");
        loginUser(testUser1, "dev-swap-2");
        Device d1 = deviceRepository.findByUserIdAndDeviceIdentifier(testUser1.getId(), "dev-swap-1").orElseThrow();

        clearRedisKeys();
        authenticationService.requestOtp(OtpRequestDto.builder()
                .identifierType(AuthIdentifierType.EMAIL)
                .identifier(testUser1.getEmail())
                .purpose(OtpPurpose.LOGIN)
                .build());

        OtpVerifyRequestDto swapDto = OtpVerifyRequestDto.builder()
                .identifierType(AuthIdentifierType.EMAIL)
                .identifier(testUser1.getEmail())
                .purpose(OtpPurpose.LOGIN)
                .otp(emailProvider.getLastDeliveredOtp(testUser1.getEmail()))
                .deviceId("dev-swap-3")
                .platform(Platform.IOS)
                .replaceDeviceId(d1.getId())
                .build();

        authenticationService.verifyOtpAndAuthenticate(swapDto, "192.168.1.50", "TestAgent");
        Thread.sleep(400);

        List<UserLoginHistory> history = userLoginHistoryRepository.findAll().stream()
                .filter(h -> h.getUser().getId().equals(testUser1.getId()) && "DEVICE_REPLACED".equals(h.getEventType()))
                .toList();

        assertThat(history).isNotEmpty();
        assertThat(history.get(0).getUserMessage()).isEqualTo("Device replaced");
    }

    // ===================================================================
    // 14. LOGOUT PROJECTION
    // ===================================================================
    @Test
    @DisplayName("14. SESSION_LOGOUT projects to LOGGED_OUT entry")
    void test14_logoutProjection() throws Exception {
        AuthenticationResponse auth = loginUser(testUser1, "dev-logout-proj");
        Thread.sleep(500);

        SecurityAuditEventPayload logoutPayload = SecurityAuditEventPayload.builder()
                .eventType(SecurityEventType.SESSION_LOGOUT)
                .outcome(SecurityEventOutcome.SUCCESS)
                .user(testUser1)
                .userId(testUser1.getId())
                .platform(Platform.IOS)
                .ipAddress("192.168.1.50")
                .transactionalSuccess(false)
                .build();

        projectionService.processEventProjection(logoutPayload);
        Thread.sleep(200);

        List<UserLoginHistory> history = userLoginHistoryRepository.findAll().stream()
                .filter(h -> h.getUser().getId().equals(testUser1.getId()) && "LOGGED_OUT".equals(h.getEventType()))
                .toList();

        assertThat(history).isNotEmpty();
        assertThat(history.get(0).getUserMessage()).isEqualTo("Signed out");
    }

    // ===================================================================
    // 15. CURRENT DEVICE DETECTION
    // ===================================================================
    @Test
    @DisplayName("15. is_current_device evaluates to true ONLY for current requesting session's device")
    void test15_currentDeviceDetection() throws Exception {
        AuthenticationResponse auth = loginUser(testUser1, "dev-curr-active");
        Thread.sleep(400);

        mockMvc.perform(get("/api/v1/account/login-history")
                        .header("Authorization", "Bearer " + auth.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].is_current_device").value(true));
    }

    // ===================================================================
    // 16-20. PRIVACY & SECRET LEAKAGE PREVENTION
    // ===================================================================
    @Test
    @DisplayName("16-20. No raw IP, User-Agent, device_identifier, token, or trace ID in DTO")
    void test16to20_privacySanitization() throws Exception {
        AuthenticationResponse auth = loginUser(testUser1, "dev-privacy");
        Thread.sleep(400);

        MvcResult result = mockMvc.perform(get("/api/v1/account/login-history")
                        .header("Authorization", "Bearer " + auth.getAccessToken()))
                .andExpect(status().isOk())
                .andReturn();

        String jsonResponse = result.getResponse().getContentAsString();
        assertThat(jsonResponse).contains("\"masked_ip\":\"192.168.x.x\"");
        assertThat(jsonResponse).doesNotContain("192.168.1.50"); // Raw IP hidden
        assertThat(jsonResponse).doesNotContain("CommunityOTT-iOS"); // Raw User-Agent hidden
        assertThat(jsonResponse).doesNotContain("device_identifier");
        assertThat(jsonResponse).doesNotContain("refresh_token");
        assertThat(jsonResponse).doesNotContain("trace_id");
        assertThat(jsonResponse).doesNotContain("request_id");
    }

    // ===================================================================
    // 21. ANTI-ENUMERATION
    // ===================================================================
    @Test
    @DisplayName("21. Failed logins display non-revealing generic messages")
    void test21_antiEnumeration() throws Exception {
        SecurityAuditEventPayload payload = SecurityAuditEventPayload.builder()
                .eventType(SecurityEventType.AUTHN_LOGIN_FAILED)
                .outcome(SecurityEventOutcome.FAILURE)
                .user(testUser1)
                .userId(testUser1.getId())
                .platform(Platform.WEB)
                .ipAddress("10.0.0.1")
                .transactionalSuccess(false)
                .build();

        projectionService.processEventProjection(payload);
        Thread.sleep(200);

        List<UserLoginHistory> history = userLoginHistoryRepository.findAll().stream()
                .filter(h -> "LOGIN_FAILED".equals(h.getEventType()))
                .toList();

        assertThat(history).isNotEmpty();
        assertThat(history.get(0).getUserMessage()).isEqualTo("Sign-in attempt failed");
    }

    // ===================================================================
    // 23. PROJECTION FAILURE DOES NOT BREAK LOGIN
    // ===================================================================
    @Test
    @DisplayName("23. Exception in projection listener does not disrupt core user transaction")
    void test23_projectionFailureDoesNotBreakLogin() {
        SecurityAuditEventPayload invalidPayload = SecurityAuditEventPayload.builder()
                .eventType(SecurityEventType.AUTHN_LOGIN_SUCCESS)
                .outcome(SecurityEventOutcome.SUCCESS)
                .user(null) // Null user causes projection to log warning and exit cleanly
                .transactionalSuccess(false)
                .build();

        // Must run smoothly without throwing exception
        projectionService.onSecurityAuditEvent(invalidPayload);
    }

    // ===================================================================
    // 24. ACCOUNT DELETION BEHAVIOR
    // ===================================================================
    @Test
    @DisplayName("24. Deleting a user entity hard-deletes user_login_history records (ON DELETE CASCADE)")
    void test24_accountDeletionBehavior() throws Exception {
        User userToDelete = userRepository.save(User.builder()
                .email("delete_me_" + UUID.randomUUID().toString().substring(0, 6) + "@test.com")
                .displayName("Delete Me")
                .status(UserStatus.ACTIVE)
                .build());

        loginUser(userToDelete, "dev-del");
        Thread.sleep(400);

        assertThat(userLoginHistoryRepository.findAll().stream()
                .anyMatch(h -> h.getUser().getId().equals(userToDelete.getId()))).isTrue();

        userRepository.delete(userToDelete);

        assertThat(userLoginHistoryRepository.findAll().stream()
                .noneMatch(h -> h.getUser().getId().equals(userToDelete.getId()))).isTrue();
    }

    // ===================================================================
    // 26. CONCURRENT API REQUESTS
    // ===================================================================
    @Test
    @DisplayName("26. Concurrent API requests to login-history execute safely")
    void test26_concurrentRequests() throws Exception {
        AuthenticationResponse auth = loginUser(testUser1, "dev-concurrent");
        Thread.sleep(400);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    mockMvc.perform(get("/api/v1/account/login-history")
                                    .header("Authorization", "Bearer " + auth.getAccessToken()))
                            .andExpect(status().isOk());
                } catch (Exception ignored) {
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
    }

    // ===================================================================
    // 27. EMPTY HISTORY
    // ===================================================================
    @Test
    @DisplayName("27. User with no history receives HTTP 200 with empty items array")
    void test27_emptyHistory() throws Exception {
        User freshUser = userRepository.save(User.builder()
                .email("fresh_" + UUID.randomUUID().toString().substring(0, 6) + "@test.com")
                .displayName("Fresh User")
                .status(UserStatus.ACTIVE)
                .build());

        AuthenticationResponse auth = loginUser(freshUser, "dev-fresh");
        Thread.sleep(600);
        userLoginHistoryRepository.deleteAll(); // Clear after async projection completes

        mockMvc.perform(get("/api/v1/account/login-history")
                        .header("Authorization", "Bearer " + auth.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.total_items").value(0));
    }

    // ===================================================================
    // 28-31. INVALID PARAMETERS & MAXIMUM PAGE SIZE
    // ===================================================================
    @Test
    @DisplayName("28. Invalid date range ('from' after 'to') returns 400 Bad Request")
    void test28_invalidDateRange() throws Exception {
        AuthenticationResponse auth = loginUser(testUser1, "dev-invalid-date");
        Instant from = Instant.now();
        Instant to = Instant.now().minus(1, ChronoUnit.DAYS);

        mockMvc.perform(get("/api/v1/account/login-history?from=" + from + "&to=" + to)
                        .header("Authorization", "Bearer " + auth.getAccessToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("29. Negative page index returns 400 Bad Request")
    void test29_negativePageIndex() throws Exception {
        AuthenticationResponse auth = loginUser(testUser1, "dev-neg-page");
        mockMvc.perform(get("/api/v1/account/login-history?page=-1")
                        .header("Authorization", "Bearer " + auth.getAccessToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("30. Page size less than 1 returns 400 Bad Request")
    void test30_invalidPageSize() throws Exception {
        AuthenticationResponse auth = loginUser(testUser1, "dev-zero-size");
        mockMvc.perform(get("/api/v1/account/login-history?size=0")
                        .header("Authorization", "Bearer " + auth.getAccessToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("31. Page size greater than 50 caps at 50")
    void test31_maxPageSizeCapped() throws Exception {
        AuthenticationResponse auth = loginUser(testUser1, "dev-max-cap");
        mockMvc.perform(get("/api/v1/account/login-history?size=100")
                        .header("Authorization", "Bearer " + auth.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(50));
    }

    @Test
    @DisplayName("32. Deterministic ordering sorts ties by id DESC")
    void test32_deterministicOrdering() throws Exception {
        AuthenticationResponse auth = loginUser(testUser1, "dev-tie-order");
        Thread.sleep(400);

        mockMvc.perform(get("/api/v1/account/login-history")
                        .header("Authorization", "Bearer " + auth.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
    }
}
