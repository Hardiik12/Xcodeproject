package com.communityott.auth.entity;

import com.communityott.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

/**
 * JPA entity representing an authenticated user session tied to a specific client device.
 *
 * <p>Tracks refresh token hashes, device metadata, activity timestamps, and session revocation.
 * Note: Plaintext refresh tokens are NEVER stored in the database; only secure hashes
 * (e.g. SHA-256) are persisted.</p>
 */
@Entity
@Table(name = "auth_sessions")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"user", "refreshTokenHash"})
public class AuthSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "device_id", nullable = false, length = 255)
    private String deviceId;

    @Column(name = "device_name", length = 255)
    private String deviceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 32)
    private Platform platform;

    @Column(name = "refresh_token_hash", nullable = false, length = 255)
    private String refreshTokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (lastUsedAt == null) {
            lastUsedAt = now;
        }
    }

    /**
     * Determines if the session is currently active (not revoked and not expired).
     *
     * @return {@code true} if active; {@code false} if revoked or expired
     */
    public boolean isActive() {
        return revokedAt == null && expiresAt != null && expiresAt.isAfter(Instant.now());
    }
}
