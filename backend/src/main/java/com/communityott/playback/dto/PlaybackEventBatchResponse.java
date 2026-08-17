package com.communityott.playback.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Ingestion response for batch playback events.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlaybackEventBatchResponse {

    private int totalSubmitted;
    private int acceptedCount;
    private int duplicateCount;
    private int rejectedCount;
    private List<PlaybackEventResponse> results;
}
