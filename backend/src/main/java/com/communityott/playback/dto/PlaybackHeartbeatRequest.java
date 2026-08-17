package com.communityott.playback.dto;

import jakarta.validation.constraints.Min;
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
public class PlaybackHeartbeatRequest {

    @Min(value = 0, message = "Position seconds must be non-negative")
    private Integer positionSeconds;
}
