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
public class AdminUserAnalyticsResponse {

    private LocalDate startDate;
    private LocalDate endDate;
    private long totalRegisteredUsers;
    private long activeUsers;
    private long activeViewersInPeriod;
    private long totalSessionsInPeriod;
    private long totalWatchTimeSecondsInPeriod;
    private long averageWatchTimePerViewerSeconds;
}
