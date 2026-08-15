package com.communityott;

import com.communityott.auth.security.JwtProperties;
import com.communityott.auth.security.JwtTokenService;
import com.communityott.role.entity.Role;
import com.communityott.role.repository.RoleRepository;
import com.communityott.user.entity.User;
import com.communityott.user.entity.UserRole;
import com.communityott.user.entity.UserStatus;
import com.communityott.user.repository.UserRepository;
import com.communityott.user.repository.UserRoleRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@org.springframework.transaction.annotation.Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class JwtAuthenticationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    private User activeUser;
    private User managerUser;
    private User suspendedUser;
    private User deletedUser;
    private User contentManagerUser;

    @BeforeEach
    void setUp() {
        activeUser = userRepository.save(User.builder()
                .email("jwt_active_" + UUID.randomUUID().toString().substring(0, 8) + "@communityott.org")
                .displayName("JWT Active User")
                .status(UserStatus.ACTIVE)
                .build());

        managerUser = userRepository.save(User.builder()
                .email("jwt_mgr_" + UUID.randomUUID().toString().substring(0, 8) + "@communityott.org")
                .displayName("JWT Manager")
                .status(UserStatus.ACTIVE)
                .build());

        suspendedUser = userRepository.save(User.builder()
                .email("jwt_suspended_" + UUID.randomUUID().toString().substring(0, 8) + "@communityott.org")
                .displayName("JWT Suspended User")
                .status(UserStatus.SUSPENDED)
                .build());

        deletedUser = userRepository.save(User.builder()
                .email("jwt_deleted_" + UUID.randomUUID().toString().substring(0, 8) + "@communityott.org")
                .displayName("JWT Deleted User")
                .status(UserStatus.DELETED)
                .build());

        contentManagerUser = userRepository.save(User.builder()
                .email("jwt_cm_" + UUID.randomUUID().toString().substring(0, 8) + "@communityott.org")
                .displayName("JWT Content Manager")
                .status(UserStatus.ACTIVE)
                .build());

        // Assign USER role to activeUser
        Role userRole = roleRepository.findByName("USER").orElseThrow();
        userRoleRepository.save(new UserRole(activeUser, userRole));

        // Assign MANAGER role to managerUser
        Role managerRole = roleRepository.findByName("MANAGER").orElseThrow();
        userRoleRepository.save(new UserRole(managerUser, managerRole));

        // Assign CONTENT_MANAGER role to contentManagerUser
        Role cmRole = roleRepository.findByName("CONTENT_MANAGER").orElseThrow();
        userRoleRepository.save(new UserRole(contentManagerUser, cmRole));
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        if (activeUser != null) {
            userRoleRepository.deleteAll(userRoleRepository.findAll().stream().filter(ur -> ur.getUser().getId().equals(activeUser.getId())).toList());
            userRepository.delete(activeUser);
        }
        if (managerUser != null) {
            userRoleRepository.deleteAll(userRoleRepository.findAll().stream().filter(ur -> ur.getUser().getId().equals(managerUser.getId())).toList());
            userRepository.delete(managerUser);
        }
        if (suspendedUser != null) {
            userRepository.delete(suspendedUser);
        }
        if (deletedUser != null) {
            userRepository.delete(deletedUser);
        }
        if (contentManagerUser != null) {
            userRoleRepository.deleteAll(userRoleRepository.findAll().stream().filter(ur -> ur.getUser().getId().equals(contentManagerUser.getId())).toList());
            userRepository.delete(contentManagerUser);
        }
    }

    // ==========================================
    // 1. JWT GENERATION TESTS
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("TEST 1: JWT generation contains expected claims and TTL")
    void test1_JwtGeneration_ContainsExpectedClaimsAndTtl() {
        String token = jwtTokenService.generateAccessToken(activeUser);
        assertThat(token).isNotBlank();

        Claims claims = jwtTokenService.validateAndExtractClaims(token);
        assertThat(claims.getSubject()).isEqualTo(String.valueOf(activeUser.getId()));
        assertThat(claims.getIssuer()).isEqualTo(jwtProperties.getIssuer());
        assertThat(claims.getAudience()).contains(jwtProperties.getAudience());
        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.get("userId", Long.class)).isEqualTo(activeUser.getId());

        long durationSeconds = (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;
        assertThat(durationSeconds).isEqualTo(jwtProperties.getAccessTokenTtlSeconds());
    }

    @Test
    @Order(2)
    @DisplayName("TEST 2: JWT generation produces unique jti per token")
    void test2_JwtGeneration_ProducesUniqueJtiPerToken() {
        String token1 = jwtTokenService.generateAccessToken(activeUser);
        String token2 = jwtTokenService.generateAccessToken(activeUser);

        Optional<String> jti1 = jwtTokenService.extractJti(token1);
        Optional<String> jti2 = jwtTokenService.extractJti(token2);

        assertThat(jti1).isPresent();
        assertThat(jti2).isPresent();
        assertThat(jti1.get()).isNotEqualTo(jti2.get());
    }

    @Test
    @Order(3)
    @DisplayName("TEST 3: JWT does NOT contain sensitive secrets or passwords")
    void test3_JwtGeneration_DoesNotContainSensitiveCredentials() {
        String token = jwtTokenService.generateAccessToken(activeUser);
        Claims claims = jwtTokenService.validateAndExtractClaims(token);

        assertThat(claims.get("password")).isNull();
        assertThat(claims.get("secret")).isNull();
        assertThat(claims.get("otp")).isNull();
        assertThat(claims.get("permissions")).isNull(); // Permissions are evaluated via RBAC, not stored in JWT
    }

    // ==========================================
    // 2. JWT VALIDATION TESTS
    // ==========================================

    @Test
    @Order(4)
    @DisplayName("TEST 4: Valid JWT successfully resolves user ID")
    void test4_JwtValidation_ValidToken_SuccessfullyExtractsUserId() {
        String token = jwtTokenService.generateAccessToken(activeUser);
        Optional<Long> extractedUserId = jwtTokenService.extractUserId(token);

        assertThat(extractedUserId).isPresent();
        assertThat(extractedUserId.get()).isEqualTo(activeUser.getId());
    }

    @Test
    @Order(5)
    @DisplayName("TEST 5: Expired JWT is rejected")
    void test5_JwtValidation_ExpiredToken_Rejected() {
        SecretKey key = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now().minus(2, ChronoUnit.HOURS);
        Instant expiredAt = now.plusSeconds(300);

        String expiredToken = Jwts.builder()
                .subject(String.valueOf(activeUser.getId()))
                .issuer(jwtProperties.getIssuer())
                .audience().add(jwtProperties.getAudience()).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiredAt))
                .id(UUID.randomUUID().toString())
                .signWith(key)
                .compact();

        assertThat(jwtTokenService.isTokenValid(expiredToken)).isFalse();
        assertThat(jwtTokenService.extractUserId(expiredToken)).isEmpty();
    }

    @Test
    @Order(6)
    @DisplayName("TEST 6: JWT signed with wrong key is rejected")
    void test6_JwtValidation_InvalidSignature_Rejected() {
        SecretKey wrongKey = Keys.hmacShaKeyFor("a_completely_different_secret_key_minimum_256_bits_for_testing!".getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();

        String tamperedToken = Jwts.builder()
                .subject(String.valueOf(activeUser.getId()))
                .issuer(jwtProperties.getIssuer())
                .audience().add(jwtProperties.getAudience()).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(900)))
                .id(UUID.randomUUID().toString())
                .signWith(wrongKey)
                .compact();

        assertThat(jwtTokenService.isTokenValid(tamperedToken)).isFalse();
        assertThat(jwtTokenService.extractUserId(tamperedToken)).isEmpty();
    }

    @Test
    @Order(7)
    @DisplayName("TEST 7: JWT with wrong issuer is rejected")
    void test7_JwtValidation_WrongIssuer_Rejected() {
        SecretKey key = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();

        String wrongIssuerToken = Jwts.builder()
                .subject(String.valueOf(activeUser.getId()))
                .issuer("untrusted-issuer")
                .audience().add(jwtProperties.getAudience()).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(900)))
                .id(UUID.randomUUID().toString())
                .signWith(key)
                .compact();

        assertThat(jwtTokenService.isTokenValid(wrongIssuerToken)).isFalse();
        assertThat(jwtTokenService.extractUserId(wrongIssuerToken)).isEmpty();
    }

    @Test
    @Order(8)
    @DisplayName("TEST 8: JWT with wrong audience is rejected")
    void test8_JwtValidation_WrongAudience_Rejected() {
        SecretKey key = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();

        String wrongAudienceToken = Jwts.builder()
                .subject(String.valueOf(activeUser.getId()))
                .issuer(jwtProperties.getIssuer())
                .audience().add("foreign-service").and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(900)))
                .id(UUID.randomUUID().toString())
                .signWith(key)
                .compact();

        assertThat(jwtTokenService.isTokenValid(wrongAudienceToken)).isFalse();
        assertThat(jwtTokenService.extractUserId(wrongAudienceToken)).isEmpty();
    }

    @Test
    @Order(9)
    @DisplayName("TEST 9: Malformed and empty JWT tokens are rejected")
    void test9_JwtValidation_MalformedAndEmptyTokens_Rejected() {
        assertThat(jwtTokenService.isTokenValid("not.a.valid.jwt.structure")).isFalse();
        assertThat(jwtTokenService.isTokenValid("")).isFalse();
        assertThat(jwtTokenService.isTokenValid("   ")).isFalse();
    }

    // ==========================================
    // 3. SPRING SECURITY & RBAC INTEGRATION TESTS
    // ==========================================

    @Test
    @Order(10)
    @DisplayName("TEST 10: Request with no Authorization header to protected endpoint returns 401 UNAUTHORIZED")
    void test10_SpringSecurity_NoAuthHeader_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/rbac/test/user-view")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.message").value("Authentication is required"));
    }

    @Test
    @Order(11)
    @DisplayName("TEST 11: Request with malformed Bearer header returns 401 UNAUTHORIZED")
    void test11_SpringSecurity_MalformedBearerHeader_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/rbac/test/user-view")
                        .header("Authorization", "Bearer invalid-token-string")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Order(12)
    @DisplayName("TEST 12: Request with valid Bearer JWT to protected endpoint succeeds (200 OK)")
    void test12_SpringSecurity_ValidBearerJwt_ReachesProtectedEndpoint() throws Exception {
        String token = jwtTokenService.generateAccessToken(managerUser);

        mockMvc.perform(get("/api/v1/rbac/test/user-view")
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.requiredPermission").value("USER_VIEW"));
    }

    @Test
    @Order(13)
    @DisplayName("TEST 13: SUSPENDED user with valid JWT signature returns 401 UNAUTHORIZED")
    void test13_SpringSecurity_SuspendedUser_Returns401() throws Exception {
        String token = jwtTokenService.generateAccessToken(suspendedUser);

        mockMvc.perform(get("/api/v1/rbac/test/user-view")
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(14)
    @DisplayName("TEST 14: DELETED user with valid JWT signature returns 401 UNAUTHORIZED")
    void test14_SpringSecurity_DeletedUser_Returns401() throws Exception {
        String token = jwtTokenService.generateAccessToken(deletedUser);

        mockMvc.perform(get("/api/v1/rbac/test/user-view")
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(15)
    @DisplayName("TEST 15: USER role lacks USER_VIEW permission -> 403 FORBIDDEN")
    void test15_Rbac_PermissionEnforcement_ForbiddenForMissingPermission() throws Exception {
        String token = jwtTokenService.generateAccessToken(activeUser);

        // USER_VIEW is NOT granted to USER role -> 403 FORBIDDEN
        mockMvc.perform(get("/api/v1/rbac/test/user-view")
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @Order(16)
    @DisplayName("TEST 16: CONTENT_MANAGER role has VIDEO_UPLOAD -> 200 OK")
    void test16_Rbac_ContentManagerRole_CanUploadVideo() throws Exception {
        String token = jwtTokenService.generateAccessToken(contentManagerUser);

        mockMvc.perform(get("/api/v1/rbac/test/video-upload")
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.requiredPermission").value("VIDEO_UPLOAD"));
    }

    @Test
    @Order(17)
    @DisplayName("TEST 17: DevAuth X-Dev-User-Id fallback still functions when Bearer header is absent")
    void test17_DevAuth_FallbackFunctionsWhenNoBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/rbac/test/user-view")
                        .header("X-Dev-User-Id", managerUser.getId().toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @Order(18)
    @DisplayName("TEST 18: Public health and Swagger endpoints do not require JWT authentication")
    void test18_PublicEndpoints_DoNotRequireJwt() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"));

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }
}
