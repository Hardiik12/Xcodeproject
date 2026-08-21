package com.communityott.audit.publisher;

import com.communityott.audit.dto.SecurityAuditEventPayload;
import com.communityott.audit.model.SecurityEventOutcome;
import com.communityott.audit.model.SecurityEventType;
import com.communityott.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityAuditEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public void publish(SecurityAuditEventPayload payload) {
        if (payload == null) {
            return;
        }

        // Enrich with MDC correlation values if missing
        if (payload.getRequestId() == null) {
            payload.setRequestId(MDC.get("requestId"));
        }
        if (payload.getTraceId() == null) {
            payload.setTraceId(MDC.get("traceId"));
        }
        if (payload.getEventId() == null) {
            payload.setEventId(UUID.randomUUID());
        }
        if (payload.getCreatedAt() == null) {
            payload.setCreatedAt(Instant.now());
        }
        if (payload.getIpAddress() == null || payload.getIpAddress().isBlank()) {
            payload.setIpAddress("127.0.0.1");
        }

        // Set transactionalSuccess flag automatically based on outcome if not explicitly set
        if (!payload.isTransactionalSuccess() && payload.getOutcome() == SecurityEventOutcome.SUCCESS) {
            payload.setTransactionalSuccess(true);
        }

        log.debug("Publishing security audit event payload: type [{}] outcome [{}]", payload.getEventType(), payload.getOutcome());
        eventPublisher.publishEvent(payload);
    }

    public void publishSimple(SecurityEventType eventType, SecurityEventOutcome outcome, User user, String deviceIdentifier, String ipAddress) {
        SecurityAuditEventPayload payload = SecurityAuditEventPayload.builder()
                .eventType(eventType)
                .outcome(outcome)
                .user(user)
                .userId(user != null ? user.getId() : null)
                .deviceIdentifier(deviceIdentifier)
                .ipAddress(ipAddress)
                .transactionalSuccess(false)
                .build();
        publish(payload);
    }

    public void publishSimple(SecurityEventType eventType, SecurityEventOutcome outcome, Long userId, String deviceIdentifier, String ipAddress) {
        SecurityAuditEventPayload payload = SecurityAuditEventPayload.builder()
                .eventType(eventType)
                .outcome(outcome)
                .userId(userId)
                .deviceIdentifier(deviceIdentifier)
                .ipAddress(ipAddress)
                .transactionalSuccess(false)
                .build();
        publish(payload);
    }
}
