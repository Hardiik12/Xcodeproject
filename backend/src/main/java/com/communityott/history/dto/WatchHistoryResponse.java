package com.communityott.history.dto;

import com.communityott.auth.entity.Platform;
import com.communityott.content.entity.Content;
import com.communityott.content.entity.ContentStatus;
import com.communityott.content.entity.ContentType;
import com.communityott.history.entity.WatchHistory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchHistoryResponse {

    private Long id;
    private Long contentId;
    private String title;
    private String subtitle;
    private String thumbnailUrl;
    private String bannerUrl;
    private ContentType contentType;
    private ContentStatus contentStatus;
    private boolean contentAvailable;
    private Integer durationSeconds;
    private Integer watchedSeconds;
    private Double completionPercentage;
    private Boolean completed;
    private Platform platform;
    private String deviceId;
    private Instant firstWatchedAt;
    private Instant lastWatchedAt;

    public static WatchHistoryResponse from(WatchHistory history) {
        if (history == null) {
            return null;
        }

        Content content = history.getContent();
        boolean isAvailable = content != null && content.getStatus() == ContentStatus.PUBLISHED;

        return WatchHistoryResponse.builder()
                .id(history.getId())
                .contentId(content != null ? content.getId() : null)
                .title(content != null ? content.getTitle() : "Unavailable Content")
                .subtitle(content != null ? content.getSubtitle() : null)
                .thumbnailUrl(content != null ? content.getThumbnailUrl() : null)
                .bannerUrl(content != null ? content.getBannerUrl() : null)
                .contentType(content != null ? content.getContentType() : null)
                .contentStatus(content != null ? content.getStatus() : null)
                .contentAvailable(isAvailable)
                .durationSeconds(history.getDurationSeconds())
                .watchedSeconds(history.getWatchedSeconds())
                .completionPercentage(history.getCompletionPercentage())
                .completed(history.getCompleted())
                .platform(history.getPlatform())
                .deviceId(history.getDeviceId())
                .firstWatchedAt(history.getFirstWatchedAt())
                .lastWatchedAt(history.getLastWatchedAt())
                .build();
    }
}
