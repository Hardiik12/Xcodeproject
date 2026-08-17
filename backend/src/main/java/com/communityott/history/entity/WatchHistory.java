package com.communityott.history.entity;

import com.communityott.auth.entity.Platform;
import com.communityott.content.entity.Content;
import com.communityott.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "watch_history", uniqueConstraints = {
        @UniqueConstraint(name = "uq_watch_history_user_content", columnNames = {"user_id", "content_id"})
}, indexes = {
        @Index(name = "idx_watch_history_user_last_watched", columnList = "user_id, last_watched_at DESC"),
        @Index(name = "idx_watch_history_content_id", columnList = "content_id"),
        @Index(name = "idx_watch_history_session_id", columnList = "playback_session_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WatchHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_id", nullable = false)
    private Content content;

    @Column(name = "playback_session_id", length = 64)
    private String playbackSessionId;

    @Column(name = "watched_seconds", nullable = false)
    @Builder.Default
    private Integer watchedSeconds = 0;

    @Column(name = "duration_seconds", nullable = false)
    @Builder.Default
    private Integer durationSeconds = 0;

    @Column(name = "completion_percentage", nullable = false)
    @Builder.Default
    private Double completionPercentage = 0.0;

    @Column(name = "completed", nullable = false)
    @Builder.Default
    private Boolean completed = false;

    @Column(name = "device_id")
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 32)
    @Builder.Default
    private Platform platform = Platform.WEB;

    @Column(name = "first_watched_at", nullable = false)
    @Builder.Default
    private Instant firstWatchedAt = Instant.now();

    @Column(name = "last_watched_at", nullable = false)
    @Builder.Default
    private Instant lastWatchedAt = Instant.now();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
