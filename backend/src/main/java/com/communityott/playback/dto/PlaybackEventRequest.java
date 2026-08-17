package com.communityott.playback.dto;

import com.communityott.playback.entity.PlaybackEventType;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Ingestion request payload for a single client playback telemetry event.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaybackEventRequest {

    @NotBlank(message = "Event ID is required for idempotency")
    @Size(max = 64, message = "Event ID cannot exceed 64 characters")
    private String eventId;

    @NotNull(message = "Event type is required")
    @JsonAlias({"eventType", "type"})
    private PlaybackEventType eventType;

    @Min(value = 0, message = "Position seconds cannot be negative")
    private int positionSeconds;

    @Min(value = 0, message = "Duration seconds cannot be negative")
    private Integer durationSeconds;

    private Integer sequence;

    private Instant occurredAt;

    private Map<String, Object> metadata;
}
