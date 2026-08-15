package com.communityott.content.entity;

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
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "video_processing_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoProcessingJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_asset_id", nullable = false)
    private VideoAsset videoAsset;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 50)
    private ProcessingJobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private ProcessingJobStatus status = ProcessingJobStatus.QUEUED;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;

    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private Integer maxAttempts = 3;

    @Column(nullable = false)
    @Builder.Default
    private Integer priority = 0;

    @Column(name = "worker_id", length = 100)
    private String workerId;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "media_metadata_json", columnDefinition = "TEXT")
    private String mediaMetadataJson;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "last_heartbeat_at")
    private Instant lastHeartbeatAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = ProcessingJobStatus.QUEUED;
        }
        if (this.attemptCount == null) {
            this.attemptCount = 0;
        }
        if (this.maxAttempts == null) {
            this.maxAttempts = 3;
        }
        if (this.priority == null) {
            this.priority = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void markProcessing(String workerId) {
        Instant now = Instant.now();
        this.status = ProcessingJobStatus.PROCESSING;
        this.workerId = workerId;
        this.startedAt = now;
        this.lastHeartbeatAt = now;
        this.attemptCount = (this.attemptCount != null ? this.attemptCount : 0) + 1;
        this.errorCode = null;
        this.errorMessage = null;
    }

    public void markCompleted(String metadataJson) {
        Instant now = Instant.now();
        this.status = ProcessingJobStatus.COMPLETED;
        this.completedAt = now;
        this.lastHeartbeatAt = now;
        this.mediaMetadataJson = metadataJson;
        this.errorCode = null;
        this.errorMessage = null;
    }

    public void markFailed(String errorCode, String errorMessage) {
        Instant now = Instant.now();
        this.status = ProcessingJobStatus.FAILED;
        this.failedAt = now;
        this.lastHeartbeatAt = now;
        this.errorCode = errorCode;
        if (errorMessage != null && errorMessage.length() > 1000) {
            this.errorMessage = errorMessage.substring(0, 997) + "...";
        } else {
            this.errorMessage = errorMessage;
        }
    }

    public void requeue() {
        this.status = ProcessingJobStatus.QUEUED;
        this.startedAt = null;
        this.completedAt = null;
        this.failedAt = null;
        this.lastHeartbeatAt = null;
        this.workerId = null;
        this.errorCode = null;
        this.errorMessage = null;
    }

    public void heartbeat() {
        this.lastHeartbeatAt = Instant.now();
    }

    public boolean canRetry() {
        return this.status == ProcessingJobStatus.FAILED &&
                (this.attemptCount != null ? this.attemptCount : 0) < (this.maxAttempts != null ? this.maxAttempts : 3);
    }
}
