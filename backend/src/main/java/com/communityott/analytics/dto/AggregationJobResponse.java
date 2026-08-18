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
public class AggregationJobResponse {

    private String status;
    private int eventsProcessed;
    private Long lastProcessedEventId;
    private long durationMillis;
    private String message;
}
