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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "video_hls_variants", uniqueConstraints = {
        @UniqueConstraint(name = "uq_hls_variant_package_res", columnNames = {"hls_package_id", "resolution"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoHlsVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hls_package_id", nullable = false)
    private VideoHlsPackage hlsPackage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_rendition_id")
    private VideoRendition videoRendition;

    @Column(name = "resolution", nullable = false, length = 20)
    private String resolution;

    @Column(name = "width", nullable = false)
    private Integer width;

    @Column(name = "height", nullable = false)
    private Integer height;

    @Column(name = "playlist_key", nullable = false, unique = true, length = 500)
    private String playlistKey;

    @Column(name = "init_segment_key", nullable = false, length = 500)
    private String initSegmentKey;

    @Column(name = "segment_count", nullable = false)
    @Builder.Default
    private Integer segmentCount = 0;

    @Column(name = "target_duration_seconds", nullable = false)
    @Builder.Default
    private Integer targetDurationSeconds = 2;

    @Column(name = "bandwidth_bps", nullable = false)
    private Long bandwidthBps;

    @Column(name = "average_bandwidth_bps")
    private Long averageBandwidthBps;

    @Column(name = "codecs", nullable = false, length = 100)
    @Builder.Default
    private String codecs = "avc1.4d401f,mp4a.40.2";

    @Column(name = "frame_rate")
    private Double frameRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private HlsVariantStatus status = HlsVariantStatus.READY;

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
        if (this.status == null) {
            this.status = HlsVariantStatus.READY;
        }
        if (this.codecs == null) {
            this.codecs = "avc1.4d401f,mp4a.40.2";
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
