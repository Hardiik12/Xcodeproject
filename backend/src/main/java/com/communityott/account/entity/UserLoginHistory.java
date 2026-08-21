package com.communityott.account.entity;

import com.communityott.auth.entity.Platform;
import com.communityott.device.entity.Device;
import com.communityott.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "user_login_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserLoginHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    @Column(name = "device_name", nullable = false, length = 128)
    private String deviceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 32)
    private Platform platform;

    @Column(name = "os_version", length = 64)
    private String osVersion;

    @Column(name = "app_version", length = 64)
    private String appVersion;

    @Column(name = "masked_ip", nullable = false, length = 64)
    private String maskedIp;

    @Column(name = "approx_location", length = 128)
    private String approxLocation;

    @Column(name = "user_message", nullable = false, length = 255)
    private String userMessage;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
