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
public class PeriodComparisonDto {

    private long current;
    private long previous;
    private double growthPercentage;
    private String trend; // "UP", "DOWN", "FLAT"

    public static PeriodComparisonDto of(long current, long previous) {
        double growth;
        if (previous > 0) {
            growth = Math.round(((double) (current - previous) / previous) * 10000.0) / 100.0;
        } else if (current > 0) {
            growth = 100.0;
        } else {
            growth = 0.0;
        }

        String trend;
        if (growth > 0) {
            trend = "UP";
        } else if (growth < 0) {
            trend = "DOWN";
        } else {
            trend = "FLAT";
        }

        return PeriodComparisonDto.builder()
                .current(current)
                .previous(previous)
                .growthPercentage(growth)
                .trend(trend)
                .build();
    }
}
