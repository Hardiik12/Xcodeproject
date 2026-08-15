package com.communityott;

import com.communityott.auth.entity.AuthIdentifierType;
import com.communityott.auth.entity.AuthSession;
import com.communityott.auth.entity.OtpPurpose;
import com.communityott.auth.entity.OtpRequest;
import com.communityott.auth.entity.OtpRequestStatus;
import com.communityott.auth.entity.Platform;
import com.communityott.auth.repository.AuthSessionRepository;
import com.communityott.auth.repository.OtpRequestRepository;
import com.communityott.user.entity.User;
import com.communityott.user.entity.UserStatus;
import com.communityott.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class AuthDatabaseFoundationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OtpRequestRepository otpRequestRepository;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Autowired
    private EntityManager entityManager;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.builder()
                .email("auth-test-user@communityott.org")
                .phone("+19998887777")
                .displayName("Auth Test User")
                .status(UserStatus.ACTIVE)
                .build());
    }

    @Test
    @DisplayName("1. Hibernate schema validation passes with V4 Flyway migration")
    void test1_V4MigrationAndSchemaValidationSucceeds() {
        // Test context loads successfully with spring.jpa.hibernate.ddl-auto=validate
        assertThat(testUser.getId()).isNotNull();
    }

    @Test
    @DisplayName("2. OtpRequest entity mapping and enum persistence as string works")
    void test2_OtpRequestMappingAndEnumPersistenceWorks() {
        Instant expiresAt = Instant.now().plus(10, ChronoUnit.MINUTES);

        OtpRequest request = OtpRequest.builder()
                .user(testUser)
                .identifierType(AuthIdentifierType.EMAIL)
                .identifier(testUser.getEmail())
                .purpose(OtpPurpose.LOGIN)
                .status(OtpRequestStatus.REQUESTED)
                .expiresAt(expiresAt)
                .build();

        OtpRequest saved = otpRequestRepository.save(request);
        entityManager.flush();
        entityManager.clear();

        OtpRequest fetched = otpRequestRepository.findById(saved.getId()).orElseThrow();
        assertThat(fetched.getIdentifier()).isEqualTo("auth-test-user@communityott.org");
        assertThat(fetched.getIdentifierType()).isEqualTo(AuthIdentifierType.EMAIL);
        assertThat(fetched.getPurpose()).isEqualTo(OtpPurpose.LOGIN);
        assertThat(fetched.getStatus()).isEqualTo(OtpRequestStatus.REQUESTED);
        assertThat(fetched.getUser().getId()).isEqualTo(testUser.getId());
    }

    @Test
    @DisplayName("3. AuthSession entity mapping and User relationship works")
    void test3_AuthSessionMappingAndUserRelationshipWorks() {
        Instant expiresAt = Instant.now().plus(30, ChronoUnit.DAYS);

        AuthSession session = AuthSession.builder()
                .user(testUser)
                .deviceId("iOS-Device-UUID-12345")
                .deviceName("iPhone 15 Pro")
                .platform(Platform.IOS)
                .refreshTokenHash("sha256_hashed_refresh_token_string_abc123")
                .expiresAt(expiresAt)
                .ipAddress("192.168.1.100")
                .userAgent("CommunityOTT-iOS/1.0")
                .build();

        AuthSession saved = authSessionRepository.save(session);
        entityManager.flush();
        entityManager.clear();

        AuthSession fetched = authSessionRepository.findById(saved.getId()).orElseThrow();
        assertThat(fetched.getDeviceId()).isEqualTo("iOS-Device-UUID-12345");
        assertThat(fetched.getPlatform()).isEqualTo(Platform.IOS);
        assertThat(fetched.getRefreshTokenHash()).isEqualTo("sha256_hashed_refresh_token_string_abc123");
        assertThat(fetched.getUser().getId()).isEqualTo(testUser.getId());
        assertThat(fetched.isActive()).isTrue();
    }

    @Test
    @DisplayName("4. Session lookup by refresh_token_hash works")
    void test4_SessionLookupByRefreshTokenHashWorks() {
        String tokenHash = "hash_unique_test_token_987654";
        AuthSession session = AuthSession.builder()
                .user(testUser)
                .deviceId("Web-Chrome-123")
                .platform(Platform.WEB)
                .refreshTokenHash(tokenHash)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();

        authSessionRepository.save(session);
        entityManager.flush();
        entityManager.clear();

        Optional<AuthSession> found = authSessionRepository.findByRefreshTokenHash(tokenHash);
        assertThat(found).isPresent();
        assertThat(found.get().getPlatform()).isEqualTo(Platform.WEB);
    }

    @Test
    @DisplayName("5. Active sessions can be queried by user")
    void test5_ActiveSessionsQueryByUserIdWorks() {
        AuthSession session1 = AuthSession.builder()
                .user(testUser)
                .deviceId("device-1")
                .platform(Platform.IOS)
                .refreshTokenHash("hash-1")
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();

        AuthSession session2 = AuthSession.builder()
                .user(testUser)
                .deviceId("device-2")
                .platform(Platform.ANDROID)
                .refreshTokenHash("hash-2")
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();

        authSessionRepository.saveAll(List.of(session1, session2));
        entityManager.flush();
        entityManager.clear();

        List<AuthSession> activeSessions = authSessionRepository.findActiveSessionsByUserId(testUser.getId(), Instant.now());
        assertThat(activeSessions).hasSize(2);
    }

    @Test
    @DisplayName("6. Revoked sessions are excluded from active sessions query")
    void test6_RevokedSessionsExcludedFromActiveQuery() {
        AuthSession activeSession = AuthSession.builder()
                .user(testUser)
                .deviceId("active-dev")
                .platform(Platform.IOS)
                .refreshTokenHash("hash-active")
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();

        AuthSession revokedSession = AuthSession.builder()
                .user(testUser)
                .deviceId("revoked-dev")
                .platform(Platform.WEB)
                .refreshTokenHash("hash-revoked")
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .revokedAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        authSessionRepository.saveAll(List.of(activeSession, revokedSession));
        entityManager.flush();
        entityManager.clear();

        List<AuthSession> activeSessions = authSessionRepository.findActiveSessionsByUserId(testUser.getId(), Instant.now());
        assertThat(activeSessions).hasSize(1);
        assertThat(activeSessions.get(0).getDeviceId()).isEqualTo("active-dev");
        assertThat(activeSession.isActive()).isTrue();
        assertThat(revokedSession.isActive()).isFalse();
    }

    @Test
    @DisplayName("7. Expired sessions are excluded from active sessions query")
    void test7_ExpiredSessionsExcludedFromActiveQuery() {
        AuthSession expiredSession = AuthSession.builder()
                .user(testUser)
                .deviceId("expired-dev")
                .platform(Platform.ANDROID)
                .refreshTokenHash("hash-expired")
                .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        authSessionRepository.save(expiredSession);
        entityManager.flush();
        entityManager.clear();

        List<AuthSession> activeSessions = authSessionRepository.findActiveSessionsByUserId(testUser.getId(), Instant.now());
        assertThat(activeSessions).isEmpty();
        assertThat(expiredSession.isActive()).isFalse();
    }

    @Test
    @DisplayName("8. Multiple sessions can belong to one user")
    void test8_MultipleSessionsCanBelongToOneUser() {
        AuthSession iosSession = AuthSession.builder()
                .user(testUser)
                .deviceId("ios-uuid")
                .platform(Platform.IOS)
                .refreshTokenHash("hash-ios")
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();

        AuthSession webSession = AuthSession.builder()
                .user(testUser)
                .deviceId("web-uuid")
                .platform(Platform.WEB)
                .refreshTokenHash("hash-web")
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();

        authSessionRepository.saveAll(List.of(iosSession, webSession));
        entityManager.flush();
        entityManager.clear();

        long count = authSessionRepository.countActiveSessions(testUser.getId(), Instant.now());
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("9. User deletion cascades and deletes associated OtpRequest and AuthSession records")
    void test9_UserDeletionCascadesToAuthenticationRecords() {
        OtpRequest otp = OtpRequest.builder()
                .user(testUser)
                .identifierType(AuthIdentifierType.PHONE)
                .identifier(testUser.getPhone())
                .purpose(OtpPurpose.LOGIN)
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .build();

        AuthSession session = AuthSession.builder()
                .user(testUser)
                .deviceId("delete-test-dev")
                .platform(Platform.ANDROID)
                .refreshTokenHash("hash-delete-cascade")
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();

        OtpRequest savedOtp = otpRequestRepository.save(otp);
        AuthSession savedSession = authSessionRepository.save(session);
        entityManager.flush();

        // Delete user
        userRepository.delete(testUser);
        entityManager.flush();
        entityManager.clear();

        assertThat(userRepository.existsById(testUser.getId())).isFalse();
        assertThat(otpRequestRepository.existsById(savedOtp.getId())).isFalse();
        assertThat(authSessionRepository.existsById(savedSession.getId())).isFalse();
    }

    @Test
    @DisplayName("10. AuthSession.toString() does NOT expose refresh_token_hash secret")
    void test10_ToStringDoesNotExposeRefreshTokenHashSecret() {
        AuthSession session = AuthSession.builder()
                .id(100L)
                .deviceId("test-device")
                .platform(Platform.IOS)
                .refreshTokenHash("SUPER_SECRET_HASH_DO_NOT_LOG")
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();

        String toStringResult = session.toString();
        assertThat(toStringResult).doesNotContain("SUPER_SECRET_HASH_DO_NOT_LOG");
        assertThat(toStringResult).doesNotContain("refreshTokenHash");
    }
}
