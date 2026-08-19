package com.communityott.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManagerOverviewResponse {

    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate previousStartDate;
    private LocalDate previousEndDate;

    private PeriodComparisonDto views;
    private PeriodComparisonDto plays;
    private PeriodComparisonDto uniqueViewers;
    private PeriodComparisonDto watchTimeSeconds;
    private PeriodComparisonDto completedPlays;

    private double currentCompletionRate;
    private double previousCompletionRate;
    private double completionRateGrowthPercentage;

    private long bufferEvents;
    private long playbackErrors;
    private long qualityChanges;

    private List<ContentRankingItemDto> topContent;
}
