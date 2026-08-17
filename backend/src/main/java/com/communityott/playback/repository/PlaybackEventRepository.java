package com.communityott.playback.repository;

import com.communityott.playback.entity.PlaybackEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlaybackEventRepository extends JpaRepository<PlaybackEvent, Long> {

    boolean existsByEventId(String eventId);

    Optional<PlaybackEvent> findByEventId(String eventId);

    List<PlaybackEvent> findByPlaybackSessionIdOrderByOccurredAtAsc(Long playbackSessionId);

    Page<PlaybackEvent> findByUserIdOrderByOccurredAtDesc(Long userId, Pageable pageable);

    Page<PlaybackEvent> findByContentIdOrderByOccurredAtDesc(Long contentId, Pageable pageable);
}
