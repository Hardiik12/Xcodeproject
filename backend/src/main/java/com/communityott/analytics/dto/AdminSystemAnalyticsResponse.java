package com.communityott.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminSystemAnalyticsResponse {

    private Instant generatedAt;
    private long totalRegisteredUsers;
    private long totalActiveUsers;
    private long totalPublishedContent;
    private long totalActiveContent;
    private long totalVideoAssets;
    private long totalPlaybackSessions;
    private long lifetimePlays;
    private long lifetimeWatchTimeSeconds;
    private long lifetimeUniqueViewers;
    private List<PlatformMetricDto> platformSummary;
}
