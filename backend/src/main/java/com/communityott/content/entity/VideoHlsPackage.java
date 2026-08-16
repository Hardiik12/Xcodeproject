package com.communityott.content.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
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
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "video_hls_packages")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoHlsPackage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "video_asset_id", nullable = false, unique = true)
    private VideoAsset videoAsset;

    @Column(name = "processing_job_id")
    private Long processingJobId;

    @Column(name = "master_playlist_key", nullable = false, unique = true, length = 500)
    private String masterPlaylistKey;

    @Column(name = "storage_bucket", nullable = false, length = 100)
    private String storageBucket;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private HlsPackageStatus status = HlsPackageStatus.READY;

    @Column(name = "variant_count", nullable = false)
    @Builder.Default
    private Integer variantCount = 0;

    @Column(name = "target_duration_seconds", nullable = false)
    @Builder.Default
    private Integer targetDurationSeconds = 2;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    @OneToMany(mappedBy = "hlsPackage", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<VideoHlsVariant> variants = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

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
        if (this.status == null) {
            this.status = HlsPackageStatus.READY;
        }
        if (this.variantCount == null) {
            this.variantCount = 0;
        }
        if (this.targetDurationSeconds == null) {
            this.targetDurationSeconds = 2;
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
