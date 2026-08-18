package com.communityott.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

/**
 * Checkpoint entity tracking high-water mark cursor for incremental aggregation.
 */
@Entity
@Table(
        name = "analytics_aggregation_checkpoint",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_analytics_checkpoint_consumer",
                columnNames = {"consumer_name"}
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsCheckpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "consumer_name", nullable = false, length = 64)
    private String consumerName;

    @Column(name = "last_processed_event_id", nullable = false)
    @Builder.Default
    private Long lastProcessedEventId = 0L;

    @Column(name = "last_processed_occurred_at")
    private Instant lastProcessedOccurredAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    protected void onPersist() {
        this.updatedAt = Instant.now();
    }
}
