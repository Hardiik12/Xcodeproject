package com.communityott.playback.entity;

import com.communityott.content.entity.Content;
import com.communityott.content.entity.VideoAsset;
import com.communityott.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * JPA entity representing the durable watch progress and completion status of content for a user.
 *
 * <p>Persisted in PostgreSQL as the single source of truth for resume positions and continue watching.</p>
 */
@Entity
@Table(
        name = "watch_progress",
        uniqueConstraints = @UniqueConstraint(name = "uq_watch_progress_user_content", columnNames = {"user_id", "content_id"})
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_id", nullable = false)
    private Content content;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "video_asset_id", nullable = false)
    private VideoAsset videoAsset;

    @Column(name = "position_seconds", nullable = false)
    @Builder.Default
    private Integer positionSeconds = 0;

    @Column(name = "duration_seconds", nullable = false)
    @Builder.Default
    private Integer durationSeconds = 0;

    @Column(name = "completion_percentage", nullable = false)
    @Builder.Default
    private Double completionPercentage = 0.0;

    @Column(name = "completed", nullable = false)
    @Builder.Default
    private Boolean completed = false;

    @Column(name = "last_watched_at", nullable = false)
    private Instant lastWatchedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.lastWatchedAt == null) {
            this.lastWatchedAt = now;
        }
        if (this.positionSeconds == null) {
            this.positionSeconds = 0;
        }
        if (this.durationSeconds == null) {
            this.durationSeconds = 0;
        }
        if (this.completionPercentage == null) {
            this.completionPercentage = 0.0;
        }
        if (this.completed == null) {
            this.completed = false;
        }
        if (this.version == null) {
            this.version = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
