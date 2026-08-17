package com.communityott.playback.dto;

import com.communityott.content.entity.Content;
import com.communityott.content.entity.ContentType;
import com.communityott.playback.entity.WatchProgress;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContinueWatchingItemResponse {

    private Long contentId;
    private String title;
    private String subtitle;
    private String thumbnailUrl;
    private String bannerUrl;
    private ContentType contentType;
    private Integer durationSeconds;
    private Integer positionSeconds;
    private Double completionPercentage;
    private Integer remainingSeconds;
    private Instant lastWatchedAt;
    private Boolean completed;

    public static ContinueWatchingItemResponse from(WatchProgress progress) {
        if (progress == null) {
            return null;
        }

        Content content = progress.getContent();
        int duration = progress.getDurationSeconds() != null && progress.getDurationSeconds() > 0
                ? progress.getDurationSeconds()
                : (content != null && content.getDurationSeconds() != null ? content.getDurationSeconds() : 0);

        int position = progress.getPositionSeconds() != null ? progress.getPositionSeconds() : 0;
        int remaining = duration > 0 ? Math.max(0, duration - position) : 0;

        double percentage = progress.getCompletionPercentage() != null
                ? progress.getCompletionPercentage()
                : (duration > 0 ? Math.min(100.0, Math.max(0.0, (position / (double) duration) * 100.0)) : 0.0);

        return ContinueWatchingItemResponse.builder()
                .contentId(content != null ? content.getId() : null)
                .title(content != null ? content.getTitle() : "Unknown Title")
                .subtitle(content != null ? content.getSubtitle() : null)
                .thumbnailUrl(content != null ? content.getThumbnailUrl() : null)
                .bannerUrl(content != null ? content.getBannerUrl() : null)
                .contentType(content != null ? content.getContentType() : null)
                .durationSeconds(duration)
                .positionSeconds(position)
                .completionPercentage(percentage)
                .remainingSeconds(remaining)
                .lastWatchedAt(progress.getLastWatchedAt())
                .completed(progress.getCompleted())
                .build();
    }
}
