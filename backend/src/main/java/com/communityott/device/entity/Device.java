package com.communityott.device.entity;

import com.communityott.auth.entity.Platform;
import com.communityott.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * JPA entity representing a registered physical client device or app installation.
 */
@Entity
@Table(name = "devices", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_device_identifier", columnNames = {"user_id", "device_identifier"})
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString(exclude = "user")
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "device_identifier", nullable = false, length = 255)
    private String deviceIdentifier;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 32)
    private Platform platform;

    @Column(name = "device_model", length = 255)
    private String deviceModel;

    @Column(name = "os_version", length = 64)
    private String osVersion;

    @Column(name = "app_version", length = 64)
    private String appVersion;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "first_registered_at", nullable = false)
    private Instant firstRegisteredAt;

    @Column(name = "last_active_at", nullable = false)
    private Instant lastActiveAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (firstRegisteredAt == null) {
            firstRegisteredAt = now;
        }
        if (lastActiveAt == null) {
            lastActiveAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Determines if the device registration is currently active.
     */
    public boolean isActive() {
        return revokedAt == null;
    }
}
