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
public class DailyTrendPointDto {

    private LocalDate date;
    private long views;
    private long plays;
    private long uniqueViewers;
    private long watchTimeSeconds;
    private long completionCount;
    private long bufferEvents;
    private long errors;
}
