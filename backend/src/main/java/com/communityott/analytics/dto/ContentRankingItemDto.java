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
public class ContentRankingItemDto {

    private int rank;
    private Long contentId;
    private String title;
    private String thumbnailUrl;
    private long totalViews;
    private long totalPlays;
    private long uniqueViewers;
    private long totalWatchTimeSeconds;
    private long completionCount;
}
