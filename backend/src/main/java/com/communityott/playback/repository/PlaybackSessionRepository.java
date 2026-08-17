package com.communityott.playback.repository;

import com.communityott.playback.entity.PlaybackSession;
import com.communityott.playback.entity.PlaybackSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PlaybackSessionRepository extends JpaRepository<PlaybackSession, Long> {

    Optional<PlaybackSession> findBySessionId(String sessionId);

    Optional<PlaybackSession> findBySessionIdAndUserId(String sessionId, Long userId);

    List<PlaybackSession> findByUserIdOrderByStartedAtDesc(Long userId);

    List<PlaybackSession> findByStatusAndLastHeartbeatAtBefore(PlaybackSessionStatus status, Instant cutoff);
}
