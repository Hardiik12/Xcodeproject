package com.communityott.auth.service;

import com.communityott.audit.dto.SecurityAuditEventPayload;
import com.communityott.audit.model.SecurityEventOutcome;
import com.communityott.audit.model.SecurityEventType;
import com.communityott.audit.publisher.SecurityAuditEventPublisher;
import com.communityott.auth.dto.AuthSessionResponse;
import com.communityott.auth.dto.AuthenticatedUserResponse;
import com.communityott.auth.dto.AuthenticationResponse;
import com.communityott.auth.dto.OtpRequestDto;
import com.communityott.auth.dto.OtpRequestResult;
import com.communityott.auth.dto.OtpVerificationResult;
import com.communityott.auth.dto.OtpVerifyRequestDto;
import com.communityott.auth.dto.RefreshTokenRequestDto;
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
    private final com.communityott.device.service.DeviceService deviceService;
    private final SecurityAuditEventPublisher securityAuditEventPublisher;

    /**
     * Validates account presence per purpose and dispatches a new OTP.
     *
     * @param request OTP request details
     * @return Result containing request ID, cooldown seconds, and expiration
     */
    @Transactional
    public OtpRequestResult requestOtp(OtpRequestDto request) {
        String normalizedIdentifier = IdentifierNormalizer.normalize(request.getIdentifierType(), request.getIdentifier());

        Optional<User> existingUser = findUserByIdentifier(request.getIdentifierType(), normalizedIdentifier);
        validateUserEligibilityForOtpRequest(request.getPurpose(), existingUser);

        OtpRequestResult result = otpService.requestOtp(request.getIdentifierType(), normalizedIdentifier, request.getPurpose());

        securityAuditEventPublisher.publish(SecurityAuditEventPayload.builder()
                .eventType(SecurityEventType.AUTHN_OTP_REQUESTED)
                .outcome(SecurityEventOutcome.SUCCESS)
                .userId(existingUser.map(User::getId).orElse(null))
                .platform(Platform.WEB)
                .deviceIdentifier("otp-request-client")
                .build());

        return result;
    }

    /**
     * Verifies OTP code, registers/resolves the user, resolves/registers the client Device,
     * and creates an active server-side AuthSession with JWT access & refresh tokens.
     */
    @Transactional
    public AuthenticationResponse verifyOtpAndAuthenticate(OtpVerifyRequestDto request, String ipAddress, String userAgent) {
        String normalizedIdentifier = IdentifierNormalizer.normalize(request.getIdentifierType(), request.getIdentifier());

        // 1. Verify submitted OTP
        OtpVerificationResult verificationResult;
        try {
            verificationResult = otpService.verifyOtp(
                    request.getIdentifierType(),
                    normalizedIdentifier,
                    request.getPurpose(),
                    request.getOtp()
            );
        } catch (com.communityott.common.exception.OtpInvalidException | com.communityott.common.exception.OtpExpiredException ex) {
            securityAuditEventPublisher.publish(SecurityAuditEventPayload.builder()
                    .eventType(SecurityEventType.AUTHN_OTP_FAILED)
                    .outcome(SecurityEventOutcome.FAILURE)
                    .reasonCode("OTP_INVALID")
                    .deviceIdentifier(request.getDeviceId() != null ? request.getDeviceId() : "unknown-device")
                    .platform(request.getPlatform() != null ? request.getPlatform() : Platform.WEB)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .transactionalSuccess(false)
                    .build());
            throw ex;
        }

        if (!verificationResult.isVerified()) {
            securityAuditEventPublisher.publish(SecurityAuditEventPayload.builder()
                    .eventType(SecurityEventType.AUTHN_OTP_FAILED)
                    .outcome(SecurityEventOutcome.FAILURE)
                    .reasonCode("OTP_INVALID")
                    .deviceIdentifier(request.getDeviceId() != null ? request.getDeviceId() : "unknown-device")
                    .platform(request.getPlatform() != null ? request.getPlatform() : Platform.WEB)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .transactionalSuccess(false)
                    .build());
            throw new IllegalArgumentException("OTP verification failed");
        }

        // 2. Resolve or Register User Account
        User user;
        if (request.getPurpose() == OtpPurpose.REGISTRATION) {
            user = registerNewUser(request.getIdentifierType(), normalizedIdentifier);
        } else {
            user = findUserByIdentifier(request.getIdentifierType(), normalizedIdentifier)
                    .orElseThrow(AuthUserNotFoundException::new);
            validateUserStatus(user);
        }

        // 3. Resolve or Register Client Device & Enforce 2-Device Limit
        com.communityott.device.entity.Device device = deviceService.resolveOrCreateDevice(user, request);

        // 4. Create Server-side AuthSession linked to Device
        String rawRefreshToken = OtpCryptoUtils.generateRefreshToken();
        String refreshTokenHash = OtpCryptoUtils.hashIdentifier(rawRefreshToken);
        AuthSession session = createAuthSession(user, request, ipAddress, userAgent, refreshTokenHash, device);

        // 4. Generate Short-lived JWT Access Token bound to session
        String accessToken = jwtTokenService.generateAccessToken(user, session.getId());

        // 5. Query Effective User Roles
        Set<String> roles = rbacService.getUserRoles(user.getId());

        log.info("Authentication successful for user ID [{}] via platform [{}]", user.getId(), session.getPlatform());

        // Publish Security Audit Events
        securityAuditEventPublisher.publish(SecurityAuditEventPayload.builder()
                .eventType(SecurityEventType.AUTHN_LOGIN_SUCCESS)
                .outcome(SecurityEventOutcome.SUCCESS)
                .user(user)
                .userId(user.getId())
                .device(device)
                .deviceId(device != null ? device.getId() : null)
                .deviceIdentifier(session.getDeviceId())
                .session(session)
                .sessionId(session.getId())
                .platform(session.getPlatform())
                .appVersion(request.getAppVersion())
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build());

        securityAuditEventPublisher.publish(SecurityAuditEventPayload.builder()
                .eventType(SecurityEventType.SESSION_CREATED)
                .outcome(SecurityEventOutcome.SUCCESS)
                .user(user)
                .userId(user.getId())
                .device(device)
                .deviceId(device != null ? device.getId() : null)
                .deviceIdentifier(session.getDeviceId())
                .session(session)
                .sessionId(session.getId())
                .platform(session.getPlatform())
                .appVersion(request.getAppVersion())
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build());

        return AuthenticationResponse.builder()
                .authenticated(true)
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
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

    /**
     * Refreshes access and refresh tokens using a valid active refresh token.
     * Enforces token rotation and detects token reuse compromise.
     */
    @Transactional
    public AuthenticationResponse refreshTokens(RefreshTokenRequestDto request, String ipAddress, String userAgent) {
        if (request == null || request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
            securityAuditEventPublisher.publish(SecurityAuditEventPayload.builder()
                    .eventType(SecurityEventType.SESSION_REFRESH_FAILED)
                    .outcome(SecurityEventOutcome.FAILURE)
                    .reasonCode("BLANK_REFRESH_TOKEN")
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .build());
            throw new com.communityott.common.exception.InvalidRefreshTokenException("Refresh token must not be blank");
        }

        String incomingHash = OtpCryptoUtils.hashIdentifier(request.getRefreshToken().trim());

        // 1. Attempt lookup by current refresh_token_hash
        Optional<AuthSession> sessionOpt = authSessionRepository.findByRefreshTokenHash(incomingHash);

        if (sessionOpt.isEmpty()) {
            // 2. Reuse Detection Check: search by previous_refresh_token_hash
            Optional<AuthSession> reusedSessionOpt = authSessionRepository.findByPreviousRefreshTokenHash(incomingHash);
            if (reusedSessionOpt.isPresent()) {
                AuthSession reusedSession = reusedSessionOpt.get();
                log.warn("SECURITY ALERT: Refresh token reuse detected for session ID [{}] of user ID [{}]. Revoking session.",
                        reusedSession.getId(), reusedSession.getUser().getId());
                reusedSession.setRevokedAt(Instant.now());
                authSessionRepository.save(reusedSession);

                securityAuditEventPublisher.publish(SecurityAuditEventPayload.builder()
                        .eventType(SecurityEventType.SECURITY_TOKEN_REUSE)
                        .outcome(SecurityEventOutcome.BLOCKED)
                        .reasonCode("TOKEN_REUSE_DETECTED")
                        .user(reusedSession.getUser())
                        .userId(reusedSession.getUser().getId())
                        .session(reusedSession)
                        .sessionId(reusedSession.getId())
                        .device(reusedSession.getDeviceEntity())
                        .deviceId(reusedSession.getDeviceEntity() != null ? reusedSession.getDeviceEntity().getId() : null)
                        .deviceIdentifier(reusedSession.getDeviceId())
                        .platform(reusedSession.getPlatform())
                        .ipAddress(ipAddress)
                        .userAgent(userAgent)
                        .build());

                throw new com.communityott.common.exception.AuthTokenReuseException();
            }

            securityAuditEventPublisher.publish(SecurityAuditEventPayload.builder()
                    .eventType(SecurityEventType.SESSION_REFRESH_FAILED)
                    .outcome(SecurityEventOutcome.FAILURE)
                    .reasonCode("REFRESH_TOKEN_NOT_FOUND")
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .build());
            throw new com.communityott.common.exception.InvalidRefreshTokenException();
        }

        AuthSession session = sessionOpt.get();

        // 3. Verify session active state
        if (session.getRevokedAt() != null) {
            securityAuditEventPublisher.publish(SecurityAuditEventPayload.builder()
                    .eventType(SecurityEventType.SESSION_REFRESH_FAILED)
                    .outcome(SecurityEventOutcome.FAILURE)
                    .reasonCode("SESSION_REVOKED")
                    .user(session.getUser())
                    .userId(session.getUser().getId())
                    .session(session)
                    .sessionId(session.getId())
                    .deviceIdentifier(session.getDeviceId())
                    .platform(session.getPlatform())
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .build());
            throw new com.communityott.common.exception.AuthSessionRevokedException();
        }
        if (session.getExpiresAt() != null && session.getExpiresAt().isBefore(Instant.now())) {
            securityAuditEventPublisher.publish(SecurityAuditEventPayload.builder()
                    .eventType(SecurityEventType.SESSION_REFRESH_FAILED)
                    .outcome(SecurityEventOutcome.FAILURE)
                    .reasonCode("SESSION_EXPIRED")
                    .user(session.getUser())
                    .userId(session.getUser().getId())
                    .session(session)
                    .sessionId(session.getId())
                    .deviceIdentifier(session.getDeviceId())
                    .platform(session.getPlatform())
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .build());
            throw new com.communityott.common.exception.AuthSessionExpiredException();
        }

        // 4. Verify user status
        User user = session.getUser();
        validateUserStatus(user);

        // 5. Token Rotation
        String newRawRefreshToken = OtpCryptoUtils.generateRefreshToken();
        String newHash = OtpCryptoUtils.hashIdentifier(newRawRefreshToken);

        session.setPreviousRefreshTokenHash(session.getRefreshTokenHash());
        session.setRefreshTokenHash(newHash);
        session.setLastUsedAt(Instant.now());
        if (ipAddress != null) session.setIpAddress(ipAddress);
        if (userAgent != null) session.setUserAgent(userAgent);

        authSessionRepository.save(session);

        // 6. Generate NEW Access Token bound to session
        String newAccessToken = jwtTokenService.generateAccessToken(user, session.getId());
        Set<String> roles = rbacService.getUserRoles(user.getId());

        log.info("Token refresh successful for user ID [{}] session ID [{}]", user.getId(), session.getId());

        securityAuditEventPublisher.publish(SecurityAuditEventPayload.builder()
                .eventType(SecurityEventType.SESSION_REFRESH_SUCCESS)
                .outcome(SecurityEventOutcome.SUCCESS)
                .user(user)
                .userId(user.getId())
                .session(session)
                .sessionId(session.getId())
                .device(session.getDeviceEntity())
                .deviceId(session.getDeviceEntity() != null ? session.getDeviceEntity().getId() : null)
                .deviceIdentifier(session.getDeviceId())
                .platform(session.getPlatform())
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build());

        return AuthenticationResponse.builder()
                .authenticated(true)
                .accessToken(newAccessToken)
                .refreshToken(newRawRefreshToken)
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

    /**
     * Revokes the current authenticated session.
     */
    @Transactional
    public void logout(Long userId, Long sessionId) {
        log.info("Logging out user ID [{}] session ID [{}]", userId, sessionId);
        if (userId == null) {
            throw new IllegalArgumentException("User ID must not be null");
        }

        if (sessionId != null) {
            AuthSession session = authSessionRepository.findByIdAndUserId(sessionId, userId)
                    .orElse(null);
            if (session != null && session.getRevokedAt() == null) {
                session.setRevokedAt(Instant.now());
                authSessionRepository.save(session);
                log.info("Revoked session ID [{}] for user ID [{}]", sessionId, userId);

                securityAuditEventPublisher.publish(SecurityAuditEventPayload.builder()
                        .eventType(SecurityEventType.SESSION_LOGOUT)
                        .outcome(SecurityEventOutcome.SUCCESS)
                        .user(session.getUser())
                        .userId(userId)
                        .session(session)
                        .sessionId(sessionId)
                        .device(session.getDeviceEntity())
                        .deviceId(session.getDeviceEntity() != null ? session.getDeviceEntity().getId() : null)
                        .deviceIdentifier(session.getDeviceId())
                        .platform(session.getPlatform())
                        .build());
            }
        } else {
            logoutAll(userId);
        }
    }

    /**
     * Revokes all active sessions for the specified authenticated user.
     */
    @Transactional
    public void logoutAll(Long userId) {
        log.info("Logging out ALL active sessions for user ID [{}]", userId);
        if (userId == null) {
            throw new IllegalArgumentException("User ID must not be null");
        }

        java.util.List<AuthSession> activeSessions = authSessionRepository.findActiveSessionsByUserId(userId, Instant.now());
        Instant now = Instant.now();
        activeSessions.forEach(s -> s.setRevokedAt(now));
        authSessionRepository.saveAll(activeSessions);
        log.info("Successfully revoked {} active sessions for user ID [{}]", activeSessions.size(), userId);

        securityAuditEventPublisher.publish(SecurityAuditEventPayload.builder()
                .eventType(SecurityEventType.SESSION_LOGOUT_ALL)
                .outcome(SecurityEventOutcome.SUCCESS)
                .userId(userId)
                .platform(Platform.WEB)
                .deviceIdentifier("all-devices")
                .build());
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

    private AuthSession createAuthSession(User user, OtpVerifyRequestDto request, String ipAddress, String userAgent, String refreshTokenHash, com.communityott.device.entity.Device device) {
        String deviceId = (request.getDeviceId() != null && !request.getDeviceId().isBlank())
                ? request.getDeviceId().trim()
                : "device-" + UUID.randomUUID();

        String deviceName = (request.getDeviceName() != null && !request.getDeviceName().isBlank())
                ? request.getDeviceName().trim()
                : "Unknown Device";

        Platform platform = request.getPlatform() != null ? request.getPlatform() : Platform.WEB;

        Instant now = Instant.now();
        Instant expiresAt = now.plus(30, ChronoUnit.DAYS);

        AuthSession session = AuthSession.builder()
                .user(user)
                .deviceEntity(device)
                .deviceId(deviceId)
                .deviceName(deviceName)
                .platform(platform)
                .refreshTokenHash(refreshTokenHash)
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
