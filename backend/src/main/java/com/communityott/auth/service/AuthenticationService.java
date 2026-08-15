package com.communityott.auth.service;

import com.communityott.auth.dto.AuthSessionResponse;
import com.communityott.auth.dto.AuthenticatedUserResponse;
import com.communityott.auth.dto.AuthenticationResponse;
import com.communityott.auth.dto.OtpRequestDto;
import com.communityott.auth.dto.OtpRequestResult;
import com.communityott.auth.dto.OtpVerificationResult;
import com.communityott.auth.dto.OtpVerifyRequestDto;
import com.communityott.auth.entity.AuthIdentifierType;
import com.communityott.auth.entity.AuthSession;
import com.communityott.auth.entity.OtpPurpose;
import com.communityott.auth.entity.Platform;
import com.communityott.auth.repository.AuthSessionRepository;
import com.communityott.auth.security.JwtProperties;
import com.communityott.auth.security.JwtTokenService;
import com.communityott.auth.util.IdentifierNormalizer;
import com.communityott.auth.util.OtpCryptoUtils;
import com.communityott.common.exception.AuthAccountDeletedException;
import com.communityott.common.exception.AuthAccountSuspendedException;
import com.communityott.common.exception.AuthRecoveryNotAllowedException;
import com.communityott.common.exception.AuthRegistrationNotAllowedException;
import com.communityott.common.exception.AuthUserNotFoundException;
import com.communityott.common.rbac.RbacService;
import com.communityott.role.entity.Role;
import com.communityott.role.repository.RoleRepository;
import com.communityott.user.entity.User;
import com.communityott.user.entity.UserRole;
import com.communityott.user.entity.UserStatus;
import com.communityott.user.repository.UserRepository;
import com.communityott.user.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Service managing the complete OTP authentication lifecycle (Request, Verification,
 * Account Resolution/Registration, Status Validation, Role Assignment, and Session Creation).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final OtpService otpService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuthSessionRepository authSessionRepository;
    private final RbacService rbacService;
    private final JwtTokenService jwtTokenService;
    private final JwtProperties jwtProperties;

    /**
     * Validates account presence per purpose and dispatches a new OTP.
     *
     * @param request OTP request details
     * @return OtpRequestResult containing request ID and cooldown metadata
     */
    @Transactional
    public OtpRequestResult requestOtp(OtpRequestDto request) {
        String normalizedIdentifier = IdentifierNormalizer.normalize(request.getIdentifierType(), request.getIdentifier());
        Optional<User> existingUser = findUserByIdentifier(request.getIdentifierType(), normalizedIdentifier);

        validateUserEligibilityForOtpRequest(request.getPurpose(), existingUser);

        return otpService.requestOtp(request.getIdentifierType(), normalizedIdentifier, request.getPurpose());
    }

    /**
     * Verifies the submitted OTP, provisions/resolves the User account, checks status,
     * assigns default USER role during registration, creates an AuthSession, and returns
     * an authentication result.
     *
     * @param request OTP verification details
     * @param ipAddress client IP address
     * @param userAgent client User-Agent header
     * @return AuthenticationResponse
     */
    @Transactional
    public AuthenticationResponse verifyOtpAndAuthenticate(OtpVerifyRequestDto request, String ipAddress, String userAgent) {
        String normalizedIdentifier = IdentifierNormalizer.normalize(request.getIdentifierType(), request.getIdentifier());

        // 1. Verify OTP via Phase 4.2 cryptographic service
        OtpVerificationResult verifyResult = otpService.verifyOtp(
                request.getIdentifierType(),
                normalizedIdentifier,
                request.getPurpose(),
                request.getOtp()
        );

        // 2. Resolve or Provision User
        User user;
        if (request.getPurpose() == OtpPurpose.REGISTRATION) {
            user = registerNewUser(request.getIdentifierType(), normalizedIdentifier);
        } else {
            user = findUserByIdentifier(request.getIdentifierType(), normalizedIdentifier)
                    .orElseThrow(AuthUserNotFoundException::new);
            validateUserStatus(user);
        }

        // 3. Create Server-side AuthSession
        AuthSession session = createAuthSession(user, request, ipAddress, userAgent);

        // 4. Generate Short-lived JWT Access Token
        String accessToken = jwtTokenService.generateAccessToken(user);

        // 5. Query Effective User Roles
        Set<String> roles = rbacService.getUserRoles(user.getId());

        log.info("Authentication successful for user ID [{}] via platform [{}]", user.getId(), session.getPlatform());

        return AuthenticationResponse.builder()
                .authenticated(true)
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getAccessTokenTtlSeconds())
                .user(AuthenticatedUserResponse.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .phone(user.getPhone())
                        .displayName(user.getDisplayName())
                        .status(user.getStatus())
                        .roles(roles)
                        .build())
                .session(AuthSessionResponse.builder()
                        .id(session.getId())
                        .deviceId(session.getDeviceId())
                        .deviceName(session.getDeviceName())
                        .platform(session.getPlatform())
                        .createdAt(session.getCreatedAt())
                        .expiresAt(session.getExpiresAt())
                        .build())
                .build();
    }

    private void validateUserEligibilityForOtpRequest(OtpPurpose purpose, Optional<User> existingUser) {
        switch (purpose) {
            case LOGIN -> {
                if (existingUser.isEmpty()) {
                    throw new AuthUserNotFoundException("Account does not exist. Please register first.");
                }
                validateUserStatus(existingUser.get());
            }
            case REGISTRATION -> {
                if (existingUser.isPresent()) {
                    throw new AuthRegistrationNotAllowedException("An account with this identifier already exists. Please log in.");
                }
            }
            case ACCOUNT_RECOVERY -> {
                if (existingUser.isEmpty()) {
                    throw new AuthRecoveryNotAllowedException("No registered account found for account recovery.");
                }
                validateUserStatus(existingUser.get());
            }
        }
    }

    private void validateUserStatus(User user) {
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new AuthAccountSuspendedException();
        }
        if (user.getStatus() == UserStatus.DELETED) {
            throw new AuthAccountDeletedException();
        }
    }

    private User registerNewUser(AuthIdentifierType type, String normalizedIdentifier) {
        Optional<User> existing = findUserByIdentifier(type, normalizedIdentifier);
        if (existing.isPresent()) {
            throw new AuthRegistrationNotAllowedException();
        }

        String displayName = (type == AuthIdentifierType.EMAIL)
                ? normalizedIdentifier.substring(0, normalizedIdentifier.indexOf('@'))
                : "User";

        User.UserBuilder userBuilder = User.builder()
                .displayName(displayName)
                .status(UserStatus.ACTIVE);

        if (type == AuthIdentifierType.EMAIL) {
            userBuilder.email(normalizedIdentifier);
        } else {
            userBuilder.phone(normalizedIdentifier);
        }

        User user = userRepository.save(userBuilder.build());

        // Assign default USER role
        Role userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new IllegalStateException("Default USER role not found in database"));

        UserRole userRoleEntity = new UserRole(user, userRole);
        userRoleRepository.save(userRoleEntity);

        log.info("Registered new user ID [{}] with default USER role", user.getId());
        return user;
    }

    private AuthSession createAuthSession(User user, OtpVerifyRequestDto request, String ipAddress, String userAgent) {
        String deviceId = (request.getDeviceId() != null && !request.getDeviceId().isBlank())
                ? request.getDeviceId().trim()
                : "device-" + UUID.randomUUID();

        String deviceName = (request.getDeviceName() != null && !request.getDeviceName().isBlank())
                ? request.getDeviceName().trim()
                : "Unknown Device";

        Platform platform = request.getPlatform() != null ? request.getPlatform() : Platform.WEB;

        // Session hash placeholder until Phase 4.5 implements JWT refresh tokens
        String sessionHash = OtpCryptoUtils.hashIdentifier("session:" + user.getId() + ":" + UUID.randomUUID());

        Instant now = Instant.now();
        Instant expiresAt = now.plus(30, ChronoUnit.DAYS);

        AuthSession session = AuthSession.builder()
                .user(user)
                .deviceId(deviceId)
                .deviceName(deviceName)
                .platform(platform)
                .refreshTokenHash(sessionHash)
                .createdAt(now)
                .lastUsedAt(now)
                .expiresAt(expiresAt)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        return authSessionRepository.save(session);
    }

    private Optional<User> findUserByIdentifier(AuthIdentifierType type, String identifier) {
        return switch (type) {
            case EMAIL -> userRepository.findByEmail(identifier);
            case PHONE -> userRepository.findByPhone(identifier);
        };
    }
}
