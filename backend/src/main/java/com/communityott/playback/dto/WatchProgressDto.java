package com.communityott.playback.dto;

import com.communityott.playback.entity.WatchProgress;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchProgressDto {

    private Long contentId;
    private Long videoAssetId;
    private Integer positionSeconds;
    private Integer durationSeconds;
    private Double completionPercentage;
    private Boolean completed;
    private Instant lastWatchedAt;

    public static WatchProgressDto from(WatchProgress progress) {
        if (progress == null) {
            return null;
        }
        return WatchProgressDto.builder()
                .contentId(progress.getContent().getId())
                .videoAssetId(progress.getVideoAsset().getId())
                .positionSeconds(progress.getPositionSeconds())
                .durationSeconds(progress.getDurationSeconds())
                .completionPercentage(progress.getCompletionPercentage())
                .completed(progress.getCompleted())
                .lastWatchedAt(progress.getLastWatchedAt())
                .build();
    }
}
