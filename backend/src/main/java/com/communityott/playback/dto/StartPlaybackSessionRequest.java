package com.communityott.playback.dto;

import com.communityott.auth.entity.Platform;
import jakarta.validation.constraints.Size;
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
public class StartPlaybackSessionRequest {

    @Size(max = 255, message = "Device ID must not exceed 255 characters")
    private String deviceId;

    @Builder.Default
    private Platform platform = Platform.WEB;
}
