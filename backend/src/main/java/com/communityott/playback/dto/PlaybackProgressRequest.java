package com.communityott.playback.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
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
public class PlaybackProgressRequest {

    @NotNull(message = "Position seconds is required")
    @Min(value = 0, message = "Position seconds must be non-negative")
    private Integer positionSeconds;

    @Min(value = 0, message = "Duration seconds must be non-negative")
    private Integer durationSeconds;
}
