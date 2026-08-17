package com.communityott.playback.service;

import com.communityott.common.exception.InvalidPlaybackPositionException;
import com.communityott.content.entity.Content;
import com.communityott.content.entity.VideoAsset;
import com.communityott.playback.config.PlaybackProperties;
import com.communityott.playback.entity.WatchProgress;
import com.communityott.playback.repository.WatchProgressRepository;
import com.communityott.user.entity.User;
import com.communityott.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class WatchProgressService {

    private final WatchProgressRepository watchProgressRepository;
    private final UserRepository userRepository;
    private final PlaybackProperties playbackProperties;

    /**
     * Retrieves the resume position in seconds for the user and content.
     * Returns 0 if no prior progress exists.
     */
    @Transactional(readOnly = true)
    public int getResumePosition(Long userId, Long contentId) {
        if (userId == null || contentId == null) {
            return 0;
        }

        return watchProgressRepository.findByUserIdAndContentId(userId, contentId)
                .map(WatchProgress::getPositionSeconds)
                .orElse(0);
    }

    /**
     * Retrieves the full watch progress entity for the user and content.
     */
    @Transactional(readOnly = true)
    public Optional<WatchProgress> getWatchProgress(Long userId, Long contentId) {
        if (userId == null || contentId == null) {
            return Optional.empty();
        }
        return watchProgressRepository.findByUserIdAndContentId(userId, contentId);
    }

    /**
     * Records or updates watch progress in PostgreSQL.
     *
     * @param userId                Authenticated user ID
     * @param content               Content entity
     * @param videoAsset            VideoAsset entity
     * @param positionSeconds       Current playback position reported by client
     * @param clientDurationSeconds Duration reported by client (optional fallback)
     * @return Updated WatchProgress entity
     */
    @Transactional
    public WatchProgress recordProgress(Long userId,
                                        Content content,
                                        VideoAsset videoAsset,
                                        int positionSeconds,
                                        Integer clientDurationSeconds) {

        if (positionSeconds < 0) {
            throw new InvalidPlaybackPositionException("Playback position cannot be negative");
        }

        // Determine best known video duration
        int duration = resolveDuration(content, videoAsset, clientDurationSeconds);

        if (duration > 0 && positionSeconds > duration + 10) {
            throw new InvalidPlaybackPositionException(positionSeconds, duration);
        }

        int clampedPosition = duration > 0 ? Math.min(positionSeconds, duration) : positionSeconds;
        double completionPercent = duration > 0
                ? Math.min(100.0, Math.max(0.0, (clampedPosition / (double) duration) * 100.0))
                : 0.0;

        boolean isCompleted = completionPercent >= playbackProperties.getCompletionThresholdPercent();

        User userReference = userRepository.getReferenceById(userId);

        WatchProgress progress = watchProgressRepository.findByUserIdAndContentId(userId, content.getId())
                .orElseGet(() -> WatchProgress.builder()
                        .user(userReference)
                        .content(content)
                        .videoAsset(videoAsset)
                        .durationSeconds(duration)
                        .positionSeconds(0)
                        .completionPercentage(0.0)
                        .completed(false)
                        .lastWatchedAt(Instant.now())
                        .build());

        progress.setPositionSeconds(clampedPosition);
        if (duration > 0) {
            progress.setDurationSeconds(duration);
        }
        progress.setCompletionPercentage(completionPercent);
        // If already completed in past or reached threshold now, mark completed
        if (isCompleted || Boolean.TRUE.equals(progress.getCompleted())) {
            progress.setCompleted(true);
        }
        progress.setLastWatchedAt(Instant.now());
        progress.setVideoAsset(videoAsset);

        WatchProgress saved = watchProgressRepository.save(progress);
        log.debug("Recorded watch progress: userId={}, contentId={}, pos={}s/{}s ({}%), completed={}",
                userId, content.getId(), clampedPosition, duration, completionPercent, saved.getCompleted());

        return saved;
    }

    /**
     * Resolves the duration from content metadata, video asset metadata, or client fallback.
     */
    public int resolveDuration(Content content, VideoAsset videoAsset, Integer clientDurationSeconds) {
        if (content != null && content.getDurationSeconds() != null && content.getDurationSeconds() > 0) {
            return content.getDurationSeconds();
        }
        if (videoAsset != null && videoAsset.getDurationSeconds() != null && videoAsset.getDurationSeconds() > 0) {
            return videoAsset.getDurationSeconds();
        }
        if (clientDurationSeconds != null && clientDurationSeconds > 0) {
            return clientDurationSeconds;
        }
        return 0;
    }
}
