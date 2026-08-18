package com.communityott.analytics.dto;

import com.communityott.auth.entity.Platform;
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
public class PlatformMetricDto {

    private Platform platform;
    private long sessions;
    private long totalPlays;
    private long uniqueViewers;
    private long totalWatchTimeSeconds;
    private long completionCount;
    private long errors;
    private long bufferEvents;
}

