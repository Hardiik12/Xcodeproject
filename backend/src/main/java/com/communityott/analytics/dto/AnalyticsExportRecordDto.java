package com.communityott.analytics.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class AnalyticsExportRecordDto {

    @JsonProperty("date")
    private String date;

    @JsonProperty("content_id")
    private Long contentId;

    @JsonProperty("category_id")
    private Long categoryId;

    @JsonProperty("language_id")
    private Long languageId;

    @JsonProperty("platform")
    private String platform;

    @JsonProperty("sessions")
    private long sessions;

    @JsonProperty("plays")
    private long plays;

    @JsonProperty("unique_viewers")
    private long uniqueViewers;

    @JsonProperty("watch_time_seconds")
    private long watchTimeSeconds;

    @JsonProperty("completed_plays")
    private long completedPlays;

    @JsonProperty("completion_rate")
    private double completionRate;

    @JsonProperty("buffering_events")
    private long bufferingEvents;

    @JsonProperty("playback_errors")
    private long playbackErrors;

    @JsonProperty("quality_changes")
    private long qualityChanges;
}
