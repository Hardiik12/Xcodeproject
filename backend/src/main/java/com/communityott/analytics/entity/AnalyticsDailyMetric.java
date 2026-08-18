package com.communityott.analytics.entity;

import com.communityott.auth.entity.Platform;
import com.communityott.content.entity.Content;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Pre-aggregated daily metrics per content item and platform.
 */
@Entity
@Table(
        name = "analytics_daily_metrics",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_analytics_daily_metric",
                columnNames = {"metric_date", "content_id", "platform"}
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsDailyMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id", nullable = false)
    private Content content;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 32)
    @Builder.Default
    private Platform platform = Platform.WEB;

    @Column(name = "total_sessions", nullable = false)
    @Builder.Default
    private int totalSessions = 0;

    @Column(name = "total_plays", nullable = false)
    @Builder.Default
    private int totalPlays = 0;

    @Column(name = "unique_viewers", nullable = false)
    @Builder.Default
    private int uniqueViewers = 0;

    @Column(name = "total_watch_time_seconds", nullable = false)
    @Builder.Default
    private long totalWatchTimeSeconds = 0L;

    @Column(name = "completion_count", nullable = false)
    @Builder.Default
    private int completionCount = 0;

    @Column(name = "pause_count", nullable = false)
    @Builder.Default
    private int pauseCount = 0;

    @Column(name = "seek_count", nullable = false)
    @Builder.Default
    private int seekCount = 0;

    @Column(name = "buffer_event_count", nullable = false)
    @Builder.Default
    private int bufferEventCount = 0;

    @Column(name = "error_count", nullable = false)
    @Builder.Default
    private int errorCount = 0;

    @Column(name = "quality_change_count", nullable = false)
    @Builder.Default
    private int qualityChangeCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.platform == null) {
            this.platform = Platform.WEB;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
