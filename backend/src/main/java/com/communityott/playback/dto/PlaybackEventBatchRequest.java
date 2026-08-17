package com.communityott.playback.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request payload for batch playback event ingestion.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaybackEventBatchRequest {

    @NotEmpty(message = "Events list cannot be empty")
    @Size(max = 100, message = "Batch cannot exceed 100 playback events")
    @Valid
    private List<PlaybackEventRequest> events;
}
