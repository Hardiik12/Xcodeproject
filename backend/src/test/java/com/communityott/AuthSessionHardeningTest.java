package com.communityott;

import com.communityott.auth.delivery.DevelopmentEmailOtpDeliveryProvider;
import com.communityott.auth.dto.*;
import com.communityott.auth.entity.AuthIdentifierType;
import com.communityott.auth.entity.AuthSession;
import com.communityott.auth.entity.OtpPurpose;
import com.communityott.auth.entity.Platform;
import com.communityott.auth.repository.AuthSessionRepository;
import com.communityott.auth.security.JwtTokenService;
import com.communityott.auth.service.AuthenticationService;
import com.communityott.auth.util.OtpCryptoUtils;
import com.communityott.common.exception.*;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

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
public class AuthSessionHardeningTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DevelopmentEmailOtpDeliveryProvider emailProvider;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    private User testUser1;
    private User testUser2;

    @BeforeEach
    void setUp() {
        emailProvider.clearTestStore();
        clearRedisKeys();

        testUser1 = userRepository.save(User.builder()
                .email("user1_" + UUID.randomUUID().toString().substring(0, 8) + "@communityott.com")
                .displayName("User One")
                .status(UserStatus.ACTIVE)
                .build());

        testUser2 = userRepository.save(User.builder()
                .email("user2_" + UUID.randomUUID().toString().substring(0, 8) + "@communityott.com")
                .displayName("User Two")
                .status(UserStatus.ACTIVE)
                .build());
    }

    private void clearRedisKeys() {
        java.util.Set<String> keys = redisTemplate.keys("communityott:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private AuthenticationResponse loginUser(User user) {
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
                .deviceId("device-hardened-" + user.getId())
                .deviceName("Hardened Test Device")
                .platform(Platform.IOS)
                .build();

        return authenticationService.verifyOtpAndAuthenticate(verifyDto, "127.0.0.1", "TestAgent");
    }

    // ===================================================================
    // A. LOGIN TESTS
    // ===================================================================

    @Test
    @DisplayName("1. OTP login creates a persistent auth session in database")
    void test1_otpLoginCreatesSession() {
        AuthenticationResponse response = loginUser(testUser1);
        assertThat(response.getSession()).isNotNull();
        assertThat(response.getSession().getId()).isNotNull();

        AuthSession session = authSessionRepository.findById(response.getSession().getId()).orElse(null);
        assertThat(session).isNotNull();
        assertThat(session.getUser().getId()).isEqualTo(testUser1.getId());
    }

    @Test
    @DisplayName("2. OTP login returns signed short-lived JWT access token")
    void test2_otpLoginReturnsAccessToken() {
        AuthenticationResponse response = loginUser(testUser1);
        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(jwtTokenService.isTokenValid(response.getAccessToken())).isTrue();
        assertThat(jwtTokenService.extractUserId(response.getAccessToken())).contains(testUser1.getId());
        assertThat(jwtTokenService.extractSessionId(response.getAccessToken())).contains(response.getSession().getId());
    }

    @Test
    @DisplayName("3. OTP login returns cryptographically secure refresh token")
    void test3_otpLoginReturnsRefreshToken() {
        AuthenticationResponse response = loginUser(testUser1);
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(response.getRefreshToken().length()).isGreaterThanOrEqualTo(64);
    }

    @Test
    @DisplayName("4. Refresh token is NOT persisted plaintext in database")
    void test4_refreshTokenNotPersistedPlaintext() {
        AuthenticationResponse response = loginUser(testUser1);
        String rawRefreshToken = response.getRefreshToken();

        AuthSession session = authSessionRepository.findById(response.getSession().getId()).orElseThrow();
        assertThat(session.getRefreshTokenHash()).isNotEqualTo(rawRefreshToken);
        assertThat(session.getRefreshTokenHash()).isEqualTo(OtpCryptoUtils.hashIdentifier(rawRefreshToken));
    }

    // ===================================================================
    // B. REFRESH TESTS
    // ===================================================================

    @Test
    @DisplayName("5. Valid refresh token succeeds via API endpoint")
    void test5_validRefreshSucceeds() throws Exception {
        AuthenticationResponse auth = loginUser(testUser1);

        RefreshTokenRequestDto refreshDto = RefreshTokenRequestDto.builder()
                .refreshToken(auth.getRefreshToken())
                .build();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());
    }

    @Test
    @DisplayName("6 & 7. Refresh generates new access token and rotated refresh token")
    void test6and7_refreshRotatesTokens() {
        AuthenticationResponse auth1 = loginUser(testUser1);
        AuthenticationResponse auth2 = authenticationService.refreshTokens(
                new RefreshTokenRequestDto(auth1.getRefreshToken()), "127.0.0.1", "TestAgent"
        );

        assertThat(auth2.getAccessToken()).isNotEqualTo(auth1.getAccessToken());
        assertThat(auth2.getRefreshToken()).isNotEqualTo(auth1.getRefreshToken());
    }

    @Test
    @DisplayName("8 & REUSE. Old refresh token reuse is blocked and revokes session")
    void test8_oldRefreshTokenReusedBlockedAndRevokesSession() {
        AuthenticationResponse auth1 = loginUser(testUser1);
        String oldRefreshToken = auth1.getRefreshToken();

        // Perform legitimate refresh
        AuthenticationResponse auth2 = authenticationService.refreshTokens(
                new RefreshTokenRequestDto(oldRefreshToken), "127.0.0.1", "TestAgent"
        );
        assertThat(auth2.getRefreshToken()).isNotEqualTo(oldRefreshToken);

        // Attempt reuse of old refresh token
        assertThatThrownBy(() -> authenticationService.refreshTokens(
                new RefreshTokenRequestDto(oldRefreshToken), "127.0.0.1", "TestAgent"
        )).isInstanceOf(AuthTokenReuseException.class);

        // Session must now be revoked in DB
        AuthSession session = authSessionRepository.findById(auth1.getSession().getId()).orElseThrow();
        assertThat(session.getRevokedAt()).isNotNull();
    }

    @Test
    @DisplayName("9. Expired refresh token is rejected with 401")
    void test9_expiredRefreshRejected() {
        AuthenticationResponse auth = loginUser(testUser1);
        AuthSession session = authSessionRepository.findById(auth.getSession().getId()).orElseThrow();
        session.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
        authSessionRepository.save(session);

        assertThatThrownBy(() -> authenticationService.refreshTokens(
                new RefreshTokenRequestDto(auth.getRefreshToken()), "127.0.0.1", "TestAgent"
        )).isInstanceOf(AuthSessionExpiredException.class);
    }

    @Test
    @DisplayName("10. Revoked refresh token is rejected with 401")
    void test10_revokedRefreshRejected() {
        AuthenticationResponse auth = loginUser(testUser1);
        AuthSession session = authSessionRepository.findById(auth.getSession().getId()).orElseThrow();
        session.setRevokedAt(Instant.now());
        authSessionRepository.save(session);

        assertThatThrownBy(() -> authenticationService.refreshTokens(
                new RefreshTokenRequestDto(auth.getRefreshToken()), "127.0.0.1", "TestAgent"
        )).isInstanceOf(AuthSessionRevokedException.class);
    }

    @Test
    @DisplayName("11 & 12. Malformed/missing refresh token rejected with 400")
    void test11and12_malformedOrMissingRefreshRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"\"}"))
                .andExpect(status().isBadRequest());
    }

    // ===================================================================
    // C. SESSION REVOCATION & JWT FILTER BINDING TESTS
    // ===================================================================

    @Test
    @DisplayName("13. Valid session allows authorized request")
    void test13_validSessionWorks() throws Exception {
        AuthenticationResponse auth = loginUser(testUser1);

        mockMvc.perform(get("/api/v1/profiles")
                        .header("Authorization", "Bearer " + auth.getAccessToken()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("14. Revoked session returns 401 on API request using old JWT")
    void test14_revokedSessionReturns401() throws Exception {
        AuthenticationResponse auth = loginUser(testUser1);
        String token = auth.getAccessToken();

        // Revoke session in DB
        AuthSession session = authSessionRepository.findById(auth.getSession().getId()).orElseThrow();
        session.setRevokedAt(Instant.now());
        authSessionRepository.save(session);

        // JWT is cryptographically valid, but session is revoked -> 401
        mockMvc.perform(get("/api/v1/profiles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("15. Expired session returns 401 on API request")
    void test15_expiredSessionReturns401() throws Exception {
        AuthenticationResponse auth = loginUser(testUser1);
        String token = auth.getAccessToken();

        AuthSession session = authSessionRepository.findById(auth.getSession().getId()).orElseThrow();
        session.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
        authSessionRepository.save(session);

        mockMvc.perform(get("/api/v1/profiles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("16. Nonexistent session in JWT returns 401")
    void test16_nonexistentSessionReturns401() throws Exception {
        // Create JWT with non-existent sid claim
        String fakeToken = jwtTokenService.generateAccessToken(testUser1, 999999L);

        mockMvc.perform(get("/api/v1/profiles")
                        .header("Authorization", "Bearer " + fakeToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("17. Session belonging to another user in JWT returns 401")
    void test17_sessionMismatchReturns401() throws Exception {
        AuthenticationResponse authUser2 = loginUser(testUser2);

        // Create JWT for user1 but pointing to user2's session
        String MismatchedToken = jwtTokenService.generateAccessToken(testUser1, authUser2.getSession().getId());

        mockMvc.perform(get("/api/v1/profiles")
                        .header("Authorization", "Bearer " + MismatchedToken))
                .andExpect(status().isUnauthorized());
    }

    // ===================================================================
    // D. LOGOUT TESTS
    // ===================================================================

    @Test
    @DisplayName("18 & 19 & 20. Logout revokes current session and invalidates access & refresh tokens")
    void test18to20_logoutRevokesSession() throws Exception {
        AuthenticationResponse auth = loginUser(testUser1);
        String accessToken = auth.getAccessToken();
        String refreshToken = auth.getRefreshToken();

        // Call /logout endpoint
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        // 1. Session is revoked in DB
        AuthSession session = authSessionRepository.findById(auth.getSession().getId()).orElseThrow();
        assertThat(session.getRevokedAt()).isNotNull();

        // 2. Refresh token fails
        assertThatThrownBy(() -> authenticationService.refreshTokens(
                new RefreshTokenRequestDto(refreshToken), "127.0.0.1", "TestAgent"
        )).isInstanceOf(AuthSessionRevokedException.class);

        // 3. Access token fails
        mockMvc.perform(get("/api/v1/profiles")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    // ===================================================================
    // E. LOGOUT ALL TESTS
    // ===================================================================

    @Test
    @DisplayName("22-25. Logout-all revokes all sessions for user without affecting other users")
    void test22to25_logoutAllRevokesAllUserSessions() throws Exception {
        AuthenticationResponse auth1_deviceA = loginUser(testUser1);
        AuthenticationResponse auth1_deviceB = loginUser(testUser1);
        AuthenticationResponse authUser2 = loginUser(testUser2);

        // Call /logout-all for user 1
        mockMvc.perform(post("/api/v1/auth/logout-all")
                        .header("Authorization", "Bearer " + auth1_deviceA.getAccessToken()))
                .andExpect(status().isOk());

        // Device A access token fails
        mockMvc.perform(get("/api/v1/profiles")
                        .header("Authorization", "Bearer " + auth1_deviceA.getAccessToken()))
                .andExpect(status().isUnauthorized());

        // Device B access token fails
        mockMvc.perform(get("/api/v1/profiles")
                        .header("Authorization", "Bearer " + auth1_deviceB.getAccessToken()))
                .andExpect(status().isUnauthorized());

        // User 2 remains active and unaffected!
        mockMvc.perform(get("/api/v1/profiles")
                        .header("Authorization", "Bearer " + authUser2.getAccessToken()))
                .andExpect(status().isOk());
    }

    // ===================================================================
    // F. SECURITY TESTS
    // ===================================================================

    @Test
    @DisplayName("26. User A cannot revoke User B's session")
    void test26_userACannotRevokeUserBSession() {
        AuthenticationResponse authUser1 = loginUser(testUser1);
        AuthenticationResponse authUser2 = loginUser(testUser2);

        // User 1 tries to logout User 2's session
        authenticationService.logout(testUser1.getId(), authUser2.getSession().getId());

        // User 2's session must still be active!
        AuthSession session2 = authSessionRepository.findById(authUser2.getSession().getId()).orElseThrow();
        assertThat(session2.getRevokedAt()).isNull();
        assertThat(session2.isActive()).isTrue();
    }
}
