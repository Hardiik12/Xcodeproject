package com.communityott.playback.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Ingestion response for a single playback telemetry event.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlaybackEventResponse {

    private boolean accepted;
    private String eventId;
    private Instant receivedAt;
    private String message;

    public static PlaybackEventResponse accepted(String eventId, Instant receivedAt) {
        return PlaybackEventResponse.builder()
                .accepted(true)
                .eventId(eventId)
                .receivedAt(receivedAt != null ? receivedAt : Instant.now())
                .message("Event accepted")
                .build();
    }

    public static PlaybackEventResponse duplicate(String eventId) {
        return PlaybackEventResponse.builder()
                .accepted(true)
                .eventId(eventId)
                .receivedAt(Instant.now())
                .message("Event already processed (idempotent)")
                .build();
    }

    public static PlaybackEventResponse rejected(String eventId, String message) {
        return PlaybackEventResponse.builder()
                .accepted(false)
                .eventId(eventId)
                .receivedAt(Instant.now())
                .message(message)
                .build();
    }
}
