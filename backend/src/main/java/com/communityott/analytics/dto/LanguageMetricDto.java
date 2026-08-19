package com.communityott.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LanguageMetricDto {

    private Long languageId;
    private String languageName;
    private String languageCode;
    private long totalViews;
    private long totalPlays;
    private long uniqueViewers;
    private long totalWatchTimeSeconds;
    private long completedPlays;
    private double completionRate;
}
