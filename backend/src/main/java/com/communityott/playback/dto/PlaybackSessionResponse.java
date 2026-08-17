package com.communityott.playback.dto;

import com.communityott.content.dto.PlaybackRenditionDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO returned to client upon starting a playback session.
 *
 * <p>Contains safe playback URLs, session identifiers, resume position, and video metadata
 * without exposing internal paths or storage credentials.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaybackSessionResponse {

    private String playbackSessionId;
    private Long contentId;
    private String title;
    private String protocol;
    private String playbackUrl;
    private Instant startedAt;
    private Instant expiresAt;
    private Integer durationSeconds;
    private Integer resumePositionSeconds;
    private String deliveryMode;
    private String deliveryProvider;
    private List<PlaybackRenditionDto> availableRenditions;
}
