package com.communityott.account.entity;

import com.communityott.account.model.SecurityAlertSeverity;
import com.communityott.account.model.SecurityAlertStatus;
import com.communityott.account.model.SecurityAlertType;
import com.communityott.auth.entity.Platform;
import com.communityott.device.entity.Device;
import com.communityott.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity representing user-facing security alerts.
 */
@Entity
@Table(name = "security_alerts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecurityAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 64)
    private SecurityAlertType alertType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 32)
    private SecurityAlertSeverity severity;

    @Column(name = "title", nullable = false, length = 128)
    private String title;

    @Column(name = "message", nullable = false, length = 255)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private SecurityAlertStatus status;

    @Column(name = "source_event_id")
    private UUID sourceEventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", length = 32)
    private Platform platform;

    @Column(name = "masked_ip", length = 64)
    private String maskedIp;

    @Column(name = "approx_location", length = 128)
    private String approxLocation;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
