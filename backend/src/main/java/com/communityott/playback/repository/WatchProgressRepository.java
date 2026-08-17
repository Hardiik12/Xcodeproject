package com.communityott.playback.repository;

import com.communityott.playback.entity.WatchProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WatchProgressRepository extends JpaRepository<WatchProgress, Long> {

    Optional<WatchProgress> findByUserIdAndContentId(Long userId, Long contentId);

    List<WatchProgress> findByUserIdOrderByLastWatchedAtDesc(Long userId);
}
