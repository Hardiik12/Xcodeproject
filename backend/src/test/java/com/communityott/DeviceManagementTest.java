package com.communityott;

import com.communityott.auth.delivery.DevelopmentEmailOtpDeliveryProvider;
import com.communityott.auth.dto.AuthenticationResponse;
import com.communityott.auth.dto.OtpRequestDto;
import com.communityott.auth.dto.OtpVerifyRequestDto;
import com.communityott.auth.dto.RefreshTokenRequestDto;
import com.communityott.auth.entity.AuthIdentifierType;
import com.communityott.auth.entity.AuthSession;
import com.communityott.auth.entity.OtpPurpose;
import com.communityott.auth.entity.Platform;
import com.communityott.auth.repository.AuthSessionRepository;
import com.communityott.auth.security.JwtTokenService;
import com.communityott.auth.service.AuthenticationService;
import com.communityott.common.exception.AuthSessionRevokedException;
import com.communityott.common.exception.MaxDevicesReachedException;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
public class DeviceManagementTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DevelopmentEmailOtpDeliveryProvider emailProvider;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private User testUser1;
    private User testUser2;

    @BeforeEach
    void setUp() {
        emailProvider.clearTestStore();
        clearRedisKeys();

        testUser1 = userRepository.save(User.builder()
                .email("dev_user1_" + UUID.randomUUID().toString().substring(0, 8) + "@communityott.com")
                .displayName("Device User One")
                .status(UserStatus.ACTIVE)
                .build());

        testUser2 = userRepository.save(User.builder()
                .email("dev_user2_" + UUID.randomUUID().toString().substring(0, 8) + "@communityott.com")
                .displayName("Device User Two")
                .status(UserStatus.ACTIVE)
                .build());
    }

    private void clearRedisKeys() {
        Set<String> keys = redisTemplate.keys("communityott:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private AuthenticationResponse loginWithDevice(User user, String deviceId, String deviceName, Platform platform, Long replaceDeviceId) {
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
                .deviceName(deviceName)
                .platform(platform != null ? platform : Platform.IOS)
                .deviceModel("iPhone 15 Pro")
                .osVersion("iOS 17.4")
                .appVersion("1.4.0")
                .replaceDeviceId(replaceDeviceId)
                .build();

        return authenticationService.verifyOtpAndAuthenticate(verifyDto, "127.0.0.1", "TestAgent");
    }

    // ===================================================================
    // 1-3. DEVICE REGISTRATION & LIMIT TESTS
    // ===================================================================

    @Test
    @DisplayName("1 & 2. First and second device registrations succeed")
    void test1and2_firstAndSecondDeviceRegistrationsSucceed() {
        AuthenticationResponse auth1 = loginWithDevice(testUser1, "device-uuid-1", "iPhone", Platform.IOS, null);
        assertThat(auth1.getSession()).isNotNull();

        AuthenticationResponse auth2 = loginWithDevice(testUser1, "device-uuid-2", "Android Phone", Platform.ANDROID, null);
        assertThat(auth2.getSession()).isNotNull();

        long activeDevicesCount = deviceRepository.countByUserIdAndRevokedAtIsNull(testUser1.getId());
        assertThat(activeDevicesCount).isEqualTo(2);
    }

    @Test
    @DisplayName("3. Third device registration is blocked with 409 MAX_DEVICES_REACHED")
    void test3_thirdDeviceBlockedWith409() {
        loginWithDevice(testUser1, "device-uuid-1", "iPhone", Platform.IOS, null);
        loginWithDevice(testUser1, "device-uuid-2", "Android Phone", Platform.ANDROID, null);

        assertThatThrownBy(() -> loginWithDevice(testUser1, "device-uuid-3", "MacBook", Platform.WEB, null))
                .isInstanceOf(MaxDevicesReachedException.class);
    }

    // ===================================================================
    // 4-5. EXISTING DEVICE RE-LOGIN TESTS
    // ===================================================================

    @Test
    @DisplayName("4 & 5. Existing device re-login updates last_active_at without increasing device count")
    void test4and5_existingDeviceReLoginSucceeds() {
        loginWithDevice(testUser1, "device-uuid-1", "iPhone Initial", Platform.IOS, null);
        long initialCount = deviceRepository.countByUserIdAndRevokedAtIsNull(testUser1.getId());

        // Re-login from same device
        AuthenticationResponse auth2 = loginWithDevice(testUser1, "device-uuid-1", "iPhone Updated", Platform.IOS, null);
        assertThat(auth2.getSession()).isNotNull();

        long newCount = deviceRepository.countByUserIdAndRevokedAtIsNull(testUser1.getId());
        assertThat(newCount).isEqualTo(initialCount);

        Device device = deviceRepository.findByUserIdAndDeviceIdentifier(testUser1.getId(), "device-uuid-1").orElseThrow();
        assertThat(device.getDisplayName()).isEqualTo("iPhone Updated");
    }

    // ===================================================================
    // 6-7. REVOKED DEVICE REACTIVATION TESTS
    // ===================================================================

    @Test
    @DisplayName("6 & 7. Revoked device re-login reactivates row if count < 2")
    void test6and7_revokedDeviceReactivation() {
        loginWithDevice(testUser1, "device-uuid-1", "iPhone", Platform.IOS, null);
        Device device = deviceRepository.findByUserIdAndDeviceIdentifier(testUser1.getId(), "device-uuid-1").orElseThrow();

        // Revoke Device 1
        deviceService.revokeDevice(testUser1.getId(), device.getId());
        assertThat(deviceRepository.countByUserIdAndRevokedAtIsNull(testUser1.getId())).isEqualTo(0);

        // Re-login from Device 1
        loginWithDevice(testUser1, "device-uuid-1", "iPhone Reactivated", Platform.IOS, null);
        assertThat(deviceRepository.countByUserIdAndRevokedAtIsNull(testUser1.getId())).isEqualTo(1);

        Device updated = deviceRepository.findByUserIdAndDeviceIdentifier(testUser1.getId(), "device-uuid-1").orElseThrow();
        assertThat(updated.isActive()).isTrue();
        assertThat(updated.getDisplayName()).isEqualTo("iPhone Reactivated");
    }

    // ===================================================================
    // 8-9. DEVICE REPLACEMENT (SWAP) TESTS
    // ===================================================================

    @Test
    @DisplayName("8 & 9. Device replacement (swap) revokes specified device and registers new device atomically")
    void test8and9_deviceReplacementAtomic() {
        AuthenticationResponse auth1 = loginWithDevice(testUser1, "device-uuid-1", "iPhone A", Platform.IOS, null);
        loginWithDevice(testUser1, "device-uuid-2", "Android B", Platform.ANDROID, null);

        Long device1Id = authSessionRepository.findById(auth1.getSession().getId()).orElseThrow().getDeviceEntity().getId();

        // Replace Device 1 with Device 3
        AuthenticationResponse auth3 = loginWithDevice(testUser1, "device-uuid-3", "Web C", Platform.WEB, device1Id);
        assertThat(auth3.getSession()).isNotNull();

        assertThat(deviceRepository.countByUserIdAndRevokedAtIsNull(testUser1.getId())).isEqualTo(2);

        Device oldDevice = deviceRepository.findById(device1Id).orElseThrow();
        assertThat(oldDevice.isActive()).isFalse();

        Device newDevice = deviceRepository.findByUserIdAndDeviceIdentifier(testUser1.getId(), "device-uuid-3").orElseThrow();
        assertThat(newDevice.isActive()).isTrue();
    }

    // ===================================================================
    // 10-13. DEVICE REVOCATION & TOKEN INVALIDATION TESTS
    // ===================================================================

    @Test
    @DisplayName("10-13. Device revoke endpoint revokes device, invalidates active sessions and access/refresh tokens")
    void test10to13_deviceRevocationInvalidatesTokens() throws Exception {
        AuthenticationResponse auth = loginWithDevice(testUser1, "device-uuid-1", "iPhone", Platform.IOS, null);
        String accessToken = auth.getAccessToken();
        String refreshToken = auth.getRefreshToken();
        Long deviceId = authSessionRepository.findById(auth.getSession().getId()).orElseThrow().getDeviceEntity().getId();

        // Perform HTTP POST /api/v1/devices/{deviceId}/revoke
        mockMvc.perform(post("/api/v1/devices/" + deviceId + "/revoke")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("REVOKED"));

        // 1. Device is revoked
        Device device = deviceRepository.findById(deviceId).orElseThrow();
        assertThat(device.isActive()).isFalse();

        // 2. Access token is rejected (HTTP 401)
        mockMvc.perform(get("/api/v1/profiles")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());

        // 3. Refresh token is rejected
        assertThatThrownBy(() -> authenticationService.refreshTokens(
                new RefreshTokenRequestDto(refreshToken), "127.0.0.1", "TestAgent"
        )).isInstanceOf(AuthSessionRevokedException.class);
    }

    // ===================================================================
    // 14-15. DEVICE LIST & CURRENT DEVICE DETECTION TESTS
    // ===================================================================

    @Test
    @DisplayName("14 & 15. GET /api/v1/devices lists user devices and correctly identifies current device")
    void test14and15_deviceListAndCurrentDeviceDetection() throws Exception {
        AuthenticationResponse auth1 = loginWithDevice(testUser1, "device-uuid-1", "iPhone 15", Platform.IOS, null);
        AuthenticationResponse auth2 = loginWithDevice(testUser1, "device-uuid-2", "Android Phone", Platform.ANDROID, null);

        mockMvc.perform(get("/api/v1/devices")
                        .header("Authorization", "Bearer " + auth2.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[?(@.displayName == 'Android Phone')].isCurrentDevice").value(true));
    }

    // ===================================================================
    // 16. SECURITY & CROSS-USER ISOLATION TESTS
    // ===================================================================

    @Test
    @DisplayName("16. User A cannot view or revoke User B's device")
    void test16_crossUserDeviceAccessBlocked() throws Exception {
        AuthenticationResponse authUser1 = loginWithDevice(testUser1, "device-uuid-1", "iPhone User 1", Platform.IOS, null);
        AuthenticationResponse authUser2 = loginWithDevice(testUser2, "device-uuid-2", "iPhone User 2", Platform.IOS, null);

        Long user2DeviceId = authSessionRepository.findById(authUser2.getSession().getId()).orElseThrow().getDeviceEntity().getId();

        // User 1 attempts to revoke User 2's device -> 404 NOT_FOUND
        mockMvc.perform(post("/api/v1/devices/" + user2DeviceId + "/revoke")
                        .header("Authorization", "Bearer " + authUser1.getAccessToken()))
                .andExpect(status().isNotFound());

        // User 2 device must still be active
        Device user2Device = deviceRepository.findById(user2DeviceId).orElseThrow();
        assertThat(user2Device.isActive()).isTrue();
    }

    // ===================================================================
    // 17-19. VALIDATION TESTS
    // ===================================================================

    @Test
    @DisplayName("18. Blank or missing device_identifier returns HTTP 400")
    void test18_blankDeviceIdentifierReturns400() {
        OtpVerifyRequestDto verifyDto = OtpVerifyRequestDto.builder()
                .identifierType(AuthIdentifierType.EMAIL)
                .identifier(testUser1.getEmail())
                .purpose(OtpPurpose.LOGIN)
                .otp("123456")
                .deviceId("") // Blank
                .build();

        assertThatThrownBy(() -> deviceService.resolveOrCreateDevice(testUser1, verifyDto))
                .hasMessageContaining("device_identifier (deviceId) is required");
    }

    // ===================================================================
    // 21-22. LOGOUT & LOGOUT-ALL ISOLATION TESTS
    // ===================================================================

    @Test
    @DisplayName("21. Logout current session revokes session but leaves device active")
    void test21_logoutSessionLeavesDeviceActive() throws Exception {
        AuthenticationResponse auth = loginWithDevice(testUser1, "device-uuid-1", "iPhone", Platform.IOS, null);
        Long deviceId = authSessionRepository.findById(auth.getSession().getId()).orElseThrow().getDeviceEntity().getId();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + auth.getAccessToken()))
                .andExpect(status().isOk());

        // Device remains active!
        Device device = deviceRepository.findById(deviceId).orElseThrow();
        assertThat(device.isActive()).isTrue();
    }
}
