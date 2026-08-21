package com.communityott.account.service;

import com.communityott.account.dto.SecurityAlertResponse;
import com.communityott.account.dto.UnreadCountResponse;
import com.communityott.account.entity.SecurityAlert;
import com.communityott.account.model.SecurityAlertSeverity;
import com.communityott.account.model.SecurityAlertStatus;
import com.communityott.account.model.SecurityAlertType;
import com.communityott.account.repository.SecurityAlertRepository;
import com.communityott.audit.dto.SecurityAuditEventPayload;
import com.communityott.audit.model.SecurityEventType;
import com.communityott.auth.entity.Platform;
import com.communityott.common.exception.SecurityAlertNotFoundException;
import com.communityott.device.entity.Device;
import com.communityott.device.repository.DeviceRepository;
import com.communityott.user.entity.User;
import com.communityott.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Service managing user security alerts, asynchronous security event classification,
 * deduplication, idempotency, and user-facing alert read operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityAlertService {

    private final SecurityAlertRepository securityAlertRepository;
    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final java.util.Set<UUID> processedEventIds = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    @Async("securityAuditExecutor")
    @EventListener
    public void onSecurityAuditEvent(SecurityAuditEventPayload payload) {
        processEventAlert(payload);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processEventAlert(SecurityAuditEventPayload payload) {
        if (payload == null || payload.getEventType() == null) {
            return;
        }

        try {
            evaluateAndCreateAlert(payload);
        } catch (Exception ex) {
            log.error("Failed to process security alert for event [{}]", payload.getEventType(), ex);
        }
    }

    private void evaluateAndCreateAlert(SecurityAuditEventPayload payload) {
        SecurityAlertType alertType = mapAlertType(payload.getEventType());
        if (alertType == null) {
            return;
        }

        Long userId = payload.getUserId() != null ? payload.getUserId() :
                (payload.getUser() != null ? payload.getUser().getId() : null);

        if (userId == null) {
            return;
        }

        UUID sourceEventId = payload.getEventId();

        // 1. Exact Source Event Idempotency Check
        if (sourceEventId != null) {
            if (!processedEventIds.add(sourceEventId) || securityAlertRepository.existsByUserIdAndAlertTypeAndSourceEventId(userId, alertType, sourceEventId)) {
                log.debug("Skipped duplicate alert for event ID [{}] and user [{}]", sourceEventId, userId);
                return;
            }
        }

        Instant occurredAt = payload.getCreatedAt() != null ? payload.getCreatedAt() : Instant.now();

        // 2. 5-Minute Correlation Window Deduplication Check (Except ALERT_TOKEN_REUSE which bypasses cooldown)
        if (alertType != SecurityAlertType.ALERT_TOKEN_REUSE) {
            Instant cutoff = occurredAt.minus(5, ChronoUnit.MINUTES);
            if (securityAlertRepository.existsByUserIdAndAlertTypeAndCreatedAtAfter(userId, alertType, cutoff)) {
                log.debug("Deduplicated security alert [{}] within 5m window for user [{}]", alertType, userId);
                return;
            }
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return;
        }

        Device device = payload.getDevice();
        if (device == null && payload.getDeviceId() != null) {
            device = deviceRepository.findById(payload.getDeviceId()).orElse(null);
        }

        Platform platform = payload.getPlatform() != null ? payload.getPlatform() : Platform.WEB;
        SecurityAlertSeverity severity = mapSeverity(alertType);
        String title = mapTitle(alertType);
        String message = mapMessage(alertType);
        String maskedIp = maskIpAddress(payload.getIpAddress());
        String approxLocation = resolveApproxLocation(payload.getIpAddress());

        SecurityAlert alert = SecurityAlert.builder()
                .user(user)
                .alertType(alertType)
                .severity(severity)
                .title(title)
                .message(message)
                .status(SecurityAlertStatus.UNREAD)
                .sourceEventId(sourceEventId)
                .device(device)
                .platform(platform)
                .maskedIp(maskedIp)
                .approxLocation(approxLocation)
                .createdAt(occurredAt)
                .expiresAt(occurredAt.plus(60, ChronoUnit.DAYS))
                .build();

        try {
            securityAlertRepository.save(alert);
            log.info("Created user security alert [{}] (severity: {}) for user [{}]", alertType, severity, userId);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            log.debug("Skipped concurrent duplicate alert creation for event [{}] and user [{}]", sourceEventId, userId);
        }
    }

    @Transactional(readOnly = true)
    public Page<SecurityAlertResponse> getUserAlerts(Long userId, SecurityAlertStatus status, Pageable pageable) {
        Page<SecurityAlert> alerts;
        if (status != null) {
            alerts = securityAlertRepository.findByUserIdAndStatus(userId, status, pageable);
        } else {
            alerts = securityAlertRepository.findByUserId(userId, pageable);
        }
        return alerts.map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(Long userId) {
        long unreadCount = securityAlertRepository.countByUserIdAndStatus(userId, SecurityAlertStatus.UNREAD);
        return new UnreadCountResponse(unreadCount);
    }

    @Transactional
    public SecurityAlertResponse markAlertAsRead(Long alertId, Long userId) {
        SecurityAlert alert = securityAlertRepository.findByIdAndUserId(alertId, userId)
                .orElseThrow(() -> new SecurityAlertNotFoundException(alertId));

        if (alert.getStatus() == SecurityAlertStatus.UNREAD) {
            alert.setStatus(SecurityAlertStatus.READ);
            alert.setReadAt(Instant.now());
            alert = securityAlertRepository.save(alert);
        }

        return mapToResponse(alert);
    }

    @Transactional
    public void markAllAlertsAsRead(Long userId) {
        securityAlertRepository.markAllUnreadAsRead(
                userId,
                SecurityAlertStatus.UNREAD,
                SecurityAlertStatus.READ,
                Instant.now()
        );
    }

    @Transactional
    public int purgeExpiredAlerts() {
        return securityAlertRepository.deleteExpiredAlerts(Instant.now());
    }

    private SecurityAlertResponse mapToResponse(SecurityAlert alert) {
        return SecurityAlertResponse.builder()
                .id(alert.getId())
                .alertType(alert.getAlertType())
                .severity(alert.getSeverity())
                .title(alert.getTitle())
                .message(alert.getMessage())
                .status(alert.getStatus())
                .platform(alert.getPlatform())
                .maskedIp(alert.getMaskedIp())
                .approxLocation(alert.getApproxLocation())
                .createdAt(alert.getCreatedAt())
                .readAt(alert.getReadAt())
                .build();
    }

    private SecurityAlertType mapAlertType(SecurityEventType eventType) {
        return switch (eventType) {
            case DEVICE_REGISTERED -> SecurityAlertType.ALERT_NEW_DEVICE;
            case DEVICE_REPLACED -> SecurityAlertType.ALERT_DEVICE_REPLACED;
            case AUTHN_LOGIN_FAILED, AUTHN_OTP_FAILED -> SecurityAlertType.ALERT_SUSPICIOUS_LOGIN;
            case SECURITY_TOKEN_REUSE -> SecurityAlertType.ALERT_TOKEN_REUSE;
            default -> null;
        };
    }

    private SecurityAlertSeverity mapSeverity(SecurityAlertType alertType) {
        return switch (alertType) {
            case ALERT_NEW_DEVICE -> SecurityAlertSeverity.MEDIUM;
            case ALERT_DEVICE_REPLACED, ALERT_SUSPICIOUS_LOGIN -> SecurityAlertSeverity.HIGH;
            case ALERT_TOKEN_REUSE -> SecurityAlertSeverity.CRITICAL;
        };
    }

    private String mapTitle(SecurityAlertType alertType) {
        return switch (alertType) {
            case ALERT_NEW_DEVICE -> "New device signed in";
            case ALERT_DEVICE_REPLACED -> "Device replaced";
            case ALERT_SUSPICIOUS_LOGIN -> "Unusual sign-in activity";
            case ALERT_TOKEN_REUSE -> "Account security alert";
        };
    }

    private String mapMessage(SecurityAlertType alertType) {
        return switch (alertType) {
            case ALERT_NEW_DEVICE -> "A new device was used to sign in to your CommunityOTT account.";
            case ALERT_DEVICE_REPLACED -> "One of your registered devices was replaced.";
            case ALERT_SUSPICIOUS_LOGIN -> "We detected unusual activity while signing in to your account.";
            case ALERT_TOKEN_REUSE -> "We detected unusual activity involving your account session. Please review your recent account activity.";
        };
    }

    private String maskIpAddress(String ip) {
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            return "127.0.x.x";
        }
        if (ip.contains(":")) {
            int firstColon = ip.indexOf(':');
            int secondColon = ip.indexOf(':', firstColon + 1);
            if (secondColon != -1) {
                return ip.substring(0, secondColon) + "::xxxx";
            }
            return "2001:db8::xxxx";
        }
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
}
