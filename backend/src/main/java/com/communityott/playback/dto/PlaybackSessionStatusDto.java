package com.communityott.playback.dto;

import com.communityott.auth.entity.Platform;
import com.communityott.playback.entity.PlaybackSession;
import com.communityott.playback.entity.PlaybackSessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaybackSessionStatusDto {

    private String playbackSessionId;
    private Long contentId;
    private Long videoAssetId;
    private String deviceId;
    private Platform platform;
    private PlaybackSessionStatus status;
    private Integer lastPositionSeconds;
    private Integer durationSeconds;
    private Instant startedAt;
    private Instant lastHeartbeatAt;
    private Instant endedAt;

    public static PlaybackSessionStatusDto from(PlaybackSession session) {
        if (session == null) {
            return null;
        }
        return PlaybackSessionStatusDto.builder()
                .playbackSessionId(session.getSessionId())
                .contentId(session.getContent().getId())
                .videoAssetId(session.getVideoAsset().getId())
                .deviceId(session.getDeviceId())
                .platform(session.getPlatform())
                .status(session.getStatus())
                .lastPositionSeconds(session.getLastPositionSeconds())
                .durationSeconds(session.getDurationSeconds())
                .startedAt(session.getStartedAt())
                .lastHeartbeatAt(session.getLastHeartbeatAt())
                .endedAt(session.getEndedAt())
                .build();
    }
}
