package com.communityott.audit.dto;

import com.communityott.audit.model.SecurityEventOutcome;
import com.communityott.audit.model.SecurityEventType;
import com.communityott.auth.entity.AuthSession;
import com.communityott.auth.entity.Platform;
import com.communityott.device.entity.Device;
import com.communityott.user.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityAuditEventPayload {

    @Builder.Default
    private UUID eventId = UUID.randomUUID();

    private User user;
    private Long userId;

    private SecurityEventType eventType;
    private SecurityEventOutcome outcome;
    private String reasonCode;

    private Device device;
    private Long deviceId;
    private String deviceIdentifier;

    private AuthSession session;
    private Long sessionId;

    @Builder.Default
    private Platform platform = Platform.WEB;

    private String appVersion;
    private String ipAddress;
    private String userAgent;

    private String requestId;
    private String traceId;

    private Map<String, Object> metadata;

    @Builder.Default
    private Instant createdAt = Instant.now();

    @Builder.Default
    private boolean transactionalSuccess = false;
}
