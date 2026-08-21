package com.communityott.audit.listener;

import com.communityott.audit.dto.SecurityAuditEventPayload;
import com.communityott.audit.entity.SecurityAuditEvent;
import com.communityott.audit.repository.SecurityAuditEventRepository;
import com.communityott.auth.repository.AuthSessionRepository;
import com.communityott.device.repository.DeviceRepository;
import com.communityott.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityAuditEventListener {

    private static final Logger FALLBACK_LOGGER = LoggerFactory.getLogger("SECURITY_AUDIT_FALLBACK_LOGGER");
    private static final Set<String> PROHIBITED_KEYS = Set.of(
            "password", "otp", "token", "accesstoken", "refreshtoken",
            "tokenhash", "secret", "authorization", "cookie"
    );

    private final SecurityAuditEventRepository securityAuditEventRepository;
    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final AuthSessionRepository authSessionRepository;
    private final ObjectMapper objectMapper;

    /**
     * Listener for successful business operations executed after transaction commit.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("securityAuditExecutor")
    public void onTransactionalSuccessEvent(SecurityAuditEventPayload payload) {
        if (payload.isTransactionalSuccess()) {
            processAndPersistEvent(payload);
        }
    }

    /**
     * Listener for failure or non-transactional security events.
     */
    @EventListener
    @Async("securityAuditExecutor")
    public void onNonTransactionalEvent(SecurityAuditEventPayload payload) {
        if (!payload.isTransactionalSuccess()) {
            processAndPersistEvent(payload);
        }
    }

    public void processAndPersistEvent(SecurityAuditEventPayload payload) {
        try {
            SecurityAuditEvent entity = mapPayloadToEntity(payload);
            securityAuditEventRepository.save(entity);
            log.debug("Persisted security audit event [{}] type [{}] outcome [{}]",
                    entity.getEventId(), entity.getEventType(), entity.getOutcome());
        } catch (Exception ex) {
            log.error("Failed to persist security audit event to database. Triggering fallback logger: {}", ex.getMessage());
            writeFallbackLog(payload, ex.getMessage());
        }
    }

    private SecurityAuditEvent mapPayloadToEntity(SecurityAuditEventPayload payload) {
        SecurityAuditEvent.SecurityAuditEventBuilder builder = SecurityAuditEvent.builder()
                .eventId(payload.getEventId())
                .eventType(payload.getEventType())
                .outcome(payload.getOutcome())
                .reasonCode(payload.getReasonCode())
                .deviceIdentifier(payload.getDeviceIdentifier() != null ? payload.getDeviceIdentifier() : "unknown-device")
                .platform(payload.getPlatform())
                .appVersion(payload.getAppVersion())
                .ipAddress(payload.getIpAddress() != null ? payload.getIpAddress() : "0.0.0.0")
                .userAgent(payload.getUserAgent())
                .requestId(payload.getRequestId())
                .traceId(payload.getTraceId())
                .createdAt(payload.getCreatedAt());

        // Resolve relations safely if entities or IDs provided
        if (payload.getUser() != null) {
            builder.user(payload.getUser());
        } else if (payload.getUserId() != null) {
            userRepository.findById(payload.getUserId()).ifPresent(builder::user);
        }

        if (payload.getDevice() != null) {
            builder.device(payload.getDevice());
        } else if (payload.getDeviceId() != null) {
            deviceRepository.findById(payload.getDeviceId()).ifPresent(builder::device);
        }

        if (payload.getSession() != null) {
            builder.session(payload.getSession());
        } else if (payload.getSessionId() != null) {
            authSessionRepository.findById(payload.getSessionId()).ifPresent(builder::session);
        }

        // Sanitize and serialize metadata JSON
        if (payload.getMetadata() != null && !payload.getMetadata().isEmpty()) {
            Map<String, Object> sanitized = sanitizeMetadata(payload.getMetadata());
            try {
                builder.metadata(objectMapper.writeValueAsString(sanitized));
            } catch (Exception e) {
                log.warn("Failed to serialize security audit event metadata: {}", e.getMessage());
            }
        }

        return builder.build();
    }

    private Map<String, Object> sanitizeMetadata(Map<String, Object> input) {
        Map<String, Object> sanitized = new HashMap<>();
        input.forEach((k, v) -> {
            if (k != null && PROHIBITED_KEYS.stream().noneMatch(k.toLowerCase()::contains)) {
                sanitized.put(k, v);
            } else {
                sanitized.put(k, "[REDACTED]");
            }
        });
        return sanitized;
    }

    private void writeFallbackLog(SecurityAuditEventPayload payload, String errorReason) {
        try {
            Map<String, Object> fallbackPayload = new HashMap<>();
            fallbackPayload.put("eventId", payload.getEventId());
            fallbackPayload.put("eventType", payload.getEventType());
            fallbackPayload.put("outcome", payload.getOutcome());
            fallbackPayload.put("reasonCode", payload.getReasonCode());
            fallbackPayload.put("userId", payload.getUserId());
            fallbackPayload.put("deviceIdentifier", payload.getDeviceIdentifier());
            fallbackPayload.put("ipAddress", payload.getIpAddress());
            fallbackPayload.put("dbError", errorReason);

            String jsonStr = objectMapper.writeValueAsString(fallbackPayload);
            FALLBACK_LOGGER.error("SECURITY_AUDIT_FALLBACK: {}", jsonStr);
        } catch (Exception e) {
            FALLBACK_LOGGER.error("SECURITY_AUDIT_FALLBACK: eventId={} eventType={} outcome={} error={}",
                    payload.getEventId(), payload.getEventType(), payload.getOutcome(), errorReason);
        }
    }
}
