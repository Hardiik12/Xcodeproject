package com.communityott.account.service;

import com.communityott.account.entity.UserLoginHistory;
import com.communityott.account.repository.UserLoginHistoryRepository;
import com.communityott.audit.dto.SecurityAuditEventPayload;
import com.communityott.audit.model.SecurityEventType;
import com.communityott.auth.entity.Platform;
import com.communityott.device.entity.Device;
import com.communityott.device.repository.DeviceRepository;
import com.communityott.user.entity.User;
import com.communityott.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserLoginHistoryProjectionService {

    private static final Set<SecurityEventType> USER_VISIBLE_EVENTS = Set.of(
            SecurityEventType.AUTHN_LOGIN_SUCCESS,
            SecurityEventType.AUTHN_LOGIN_FAILED,
            SecurityEventType.DEVICE_REGISTERED,
            SecurityEventType.DEVICE_REACTIVATED,
            SecurityEventType.DEVICE_REVOKED,
            SecurityEventType.DEVICE_REPLACED,
            SecurityEventType.DEVICE_LIMIT_REACHED,
            SecurityEventType.SECURITY_TOKEN_REUSE,
            SecurityEventType.SESSION_LOGOUT,
            SecurityEventType.SESSION_LOGOUT_ALL
    );

    private final UserLoginHistoryRepository userLoginHistoryRepository;
    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;

    @Async("securityAuditExecutor")
    @EventListener
    public void onSecurityAuditEvent(SecurityAuditEventPayload payload) {
        processEventProjection(payload);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processEventProjection(SecurityAuditEventPayload payload) {
        if (payload == null || payload.getEventType() == null) {
            return;
        }

        // Only process approved user-visible security events
        if (!USER_VISIBLE_EVENTS.contains(payload.getEventType())) {
            return;
        }

        try {
            projectEventToHistory(payload);
        } catch (Exception ex) {
            log.error("Failed to project C.2 security audit event [{}] to user_login_history", payload.getEventType(), ex);
            ex.printStackTrace();
        }
    }

    private void projectEventToHistory(SecurityAuditEventPayload payload) {
        Long userId = payload.getUserId() != null ? payload.getUserId() :
                (payload.getUser() != null ? payload.getUser().getId() : null);

        if (userId == null) {
            return;
        }

        User user = userRepository.findById(userId).orElse(null);

        if (user == null) {
            return;
        }

        Instant occurredAt = payload.getCreatedAt() != null ? payload.getCreatedAt() : Instant.now();

        // 1. Deduplication window check (5-second threshold for near-simultaneous login events)
        Instant cutoff = occurredAt.minus(5, ChronoUnit.SECONDS);
        List<UserLoginHistory> recentHistory = userLoginHistoryRepository.findByUserIdAndOccurredAtAfter(userId, cutoff);

        String mappedEventType = mapEventType(payload.getEventType());
        String userMessage = mapUserMessage(payload.getEventType(), payload.getReasonCode());

        // Deduplicate simultaneous login events (e.g. AUTHN_LOGIN_SUCCESS + DEVICE_REGISTERED)
        if (payload.getEventType() == SecurityEventType.DEVICE_REGISTERED) {
            // If a LOGIN_SUCCESS entry exists within 5s, update its event_type to SIGNED_IN_NEW_DEVICE
            for (UserLoginHistory existing : recentHistory) {
                if ("LOGIN_SUCCESS".equals(existing.getEventType())) {
                    existing.setEventType("SIGNED_IN_NEW_DEVICE");
                    existing.setUserMessage("Signed in on new device");
                    userLoginHistoryRepository.save(existing);
                    log.debug("Deduplicated DEVICE_REGISTERED into existing LOGIN_SUCCESS for user [{}]", userId);
                    return;
                }
            }
            mappedEventType = "SIGNED_IN_NEW_DEVICE";
            userMessage = "Signed in on new device";
        } else if (payload.getEventType() == SecurityEventType.AUTHN_LOGIN_SUCCESS) {
            // If a SIGNED_IN_NEW_DEVICE entry exists within 5s, skip duplicate creation
            for (UserLoginHistory existing : recentHistory) {
                if ("SIGNED_IN_NEW_DEVICE".equals(existing.getEventType()) || "LOGIN_SUCCESS".equals(existing.getEventType())) {
                    log.debug("Skipped duplicate LOGIN_SUCCESS for user [{}]", userId);
                    return;
                }
            }
        }

        // 2. Resolve Device safely
        Device device = payload.getDevice();
        if (device == null && payload.getDeviceId() != null) {
            device = deviceRepository.findById(payload.getDeviceId()).orElse(null);
        }

        Platform platform = payload.getPlatform() != null ? payload.getPlatform() : Platform.WEB;
        String deviceName = resolveDeviceName(device, payload, platform);
        String maskedIp = maskIpAddress(payload.getIpAddress());
        String approxLocation = resolveApproxLocation(payload.getIpAddress());

        UserLoginHistory history = UserLoginHistory.builder()
                .user(user)
                .eventType(mappedEventType)
                .status(mapStatus(payload.getOutcome()))
                .device(device)
                .deviceName(deviceName)
                .platform(platform)
                .osVersion(device != null && device.getOsVersion() != null ? device.getOsVersion() : "N/A")
                .appVersion(payload.getAppVersion() != null ? payload.getAppVersion() : (device != null ? device.getAppVersion() : "1.0.0"))
                .maskedIp(maskedIp)
                .approxLocation(approxLocation)
                .userMessage(userMessage)
                .occurredAt(occurredAt)
                .build();

        userLoginHistoryRepository.save(history);
        log.debug("Projected user_login_history entry [{}] for user [{}]", mappedEventType, userId);
    }

    public String maskIpAddress(String ip) {
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            return "127.0.x.x";
        }
        if (ip.contains(":")) {
            // IPv6 masking
            int firstColon = ip.indexOf(':');
            int secondColon = ip.indexOf(':', firstColon + 1);
            if (secondColon != -1) {
                return ip.substring(0, secondColon) + "::xxxx";
            }
            return "2001:db8::xxxx";
        }
        // IPv4 masking
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".x.x";
        }
        return "127.0.x.x";
    }

    private String resolveApproxLocation(String ip) {
        if (ip == null || ip.isBlank() || ip.startsWith("127.") || ip.startsWith("10.") || ip.startsWith("192.168.")) {
            return "Local Network";
        }
        return "Hyderabad, India";
    }

    private String resolveDeviceName(Device device, SecurityAuditEventPayload payload, Platform platform) {
        if (device != null && device.getDisplayName() != null && !device.getDisplayName().isBlank()) {
            return device.getDisplayName();
        }
        if (payload.getDeviceIdentifier() != null && !payload.getDeviceIdentifier().isBlank()) {
            return platform + " Device";
        }
        return platform + " Device";
    }

    private String mapStatus(com.communityott.audit.model.SecurityEventOutcome outcome) {
        if (outcome == null) {
            return "SUCCESS";
        }
        return switch (outcome) {
            case SUCCESS -> "SUCCESS";
            case FAILURE -> "FAILED";
            case BLOCKED -> "BLOCKED";
        };
    }

    private String mapEventType(SecurityEventType eventType) {
        return switch (eventType) {
            case AUTHN_LOGIN_SUCCESS -> "LOGIN_SUCCESS";
            case AUTHN_LOGIN_FAILED -> "LOGIN_FAILED";
            case DEVICE_REGISTERED -> "SIGNED_IN_NEW_DEVICE";
            case DEVICE_REACTIVATED -> "DEVICE_REACTIVATED";
            case DEVICE_REVOKED -> "DEVICE_REMOVED";
            case DEVICE_REPLACED -> "DEVICE_REPLACED";
            case DEVICE_LIMIT_REACHED -> "DEVICE_LIMIT_REACHED";
            case SECURITY_TOKEN_REUSE -> "SUSPICIOUS_ACTIVITY_BLOCKED";
            case SESSION_LOGOUT -> "LOGGED_OUT";
            case SESSION_LOGOUT_ALL -> "LOGGED_OUT_ALL";
            default -> eventType.name();
        };
    }

    private String mapUserMessage(SecurityEventType eventType, String reasonCode) {
        return switch (eventType) {
            case AUTHN_LOGIN_SUCCESS -> "Signed in successfully";
            case AUTHN_LOGIN_FAILED -> "Sign-in attempt failed";
            case DEVICE_REGISTERED -> "Signed in on new device";
            case DEVICE_REACTIVATED -> "Device signed in again";
            case DEVICE_REVOKED -> "Device removed";
            case DEVICE_REPLACED -> "Device replaced";
            case DEVICE_LIMIT_REACHED -> "Sign-in blocked: maximum active devices reached";
            case SECURITY_TOKEN_REUSE -> "Suspicious sign-in attempt blocked";
            case SESSION_LOGOUT -> "Signed out";
            case SESSION_LOGOUT_ALL -> "Signed out of all devices";
            default -> "Account security activity";
        };
    }
}
