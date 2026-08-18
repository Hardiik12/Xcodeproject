package com.communityott.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsOverviewResponse {

    private LocalDate startDate;
    private LocalDate endDate;
    private long totalViews;
    private long totalPlays;
    private long uniqueViewers;
    private long totalWatchTimeSeconds;
    private long averageSessionDurationSeconds;
    private double completionRate;
    private long completedPlays;
    private long bufferEvents;
    private long playbackErrors;
    private long qualityChanges;
}
