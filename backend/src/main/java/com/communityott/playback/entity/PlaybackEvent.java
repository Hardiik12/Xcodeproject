package com.communityott.playback.entity;

import com.communityott.auth.entity.Platform;
import com.communityott.content.entity.Content;
import com.communityott.content.entity.VideoAsset;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Durable entity capturing real-time playback telemetry from OTT clients.
 */
@Entity
@Table(name = "playback_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaybackEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 64)
    private String eventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "playback_session_id", nullable = false)
    private PlaybackSession playbackSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id", nullable = false)
    private Content content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_asset_id", nullable = false)
    private VideoAsset videoAsset;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private PlaybackEventType eventType;

    @Column(name = "position_seconds", nullable = false)
    private int positionSeconds;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 32)
    private Platform platform;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "session_sequence")
    private Integer sessionSequence;

    @Column(name = "metadata", length = 4000)
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.receivedAt == null) {
            this.receivedAt = Instant.now();
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.platform == null) {
            this.platform = Platform.WEB;
        }
    }
}
