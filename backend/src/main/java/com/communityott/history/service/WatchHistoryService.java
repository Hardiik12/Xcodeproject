package com.communityott.history.service;

import com.communityott.auth.entity.Platform;
import com.communityott.content.entity.Content;
import com.communityott.history.dto.WatchHistoryResponse;
import com.communityott.history.entity.WatchHistory;
import com.communityott.history.repository.WatchHistoryRepository;
import com.communityott.playback.config.PlaybackProperties;
import com.communityott.user.entity.User;
import com.communityott.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Slf4j
@RequiredArgsConstructor
public class WatchHistoryService {

    private final WatchHistoryRepository watchHistoryRepository;
    private final UserRepository userRepository;
    private final PlaybackProperties playbackProperties;

    /**
     * Records or updates a watch history record upon meaningful playback activity.
     *
     * @param userId          Authenticated user ID
     * @param content         Content entity
     * @param sessionId       Current playback session public ID
     * @param positionSeconds Current playback position in seconds
     * @param durationSeconds Media duration in seconds
     * @param deviceId        Client device identifier
     * @param platform        Client platform (IOS, ANDROID, WEB)
     * @return Saved WatchHistory entity
     */
    @Transactional
    public WatchHistory recordViewing(Long userId,
                                      Content content,
                                      String sessionId,
                                      int positionSeconds,
                                      int durationSeconds,
                                      String deviceId,
                                      Platform platform) {

        if (userId == null || content == null) {
            return null;
        }

        int clampedPosition = durationSeconds > 0 ? Math.min(positionSeconds, durationSeconds) : Math.max(0, positionSeconds);
        double completionPercent = durationSeconds > 0
                ? Math.min(100.0, Math.max(0.0, (clampedPosition / (double) durationSeconds) * 100.0))
                : 0.0;

        boolean isCompleted = completionPercent >= playbackProperties.getCompletionThresholdPercent();
        Instant now = Instant.now();

        WatchHistory history = watchHistoryRepository.findByUserIdAndContentId(userId, content.getId())
                .orElseGet(() -> {
                    User userReference = userRepository.getReferenceById(userId);
                    return WatchHistory.builder()
                            .user(userReference)
                            .content(content)
                            .firstWatchedAt(now)
                            .build();
                });

        history.setPlaybackSessionId(sessionId);
        history.setWatchedSeconds(clampedPosition);
        if (durationSeconds > 0) {
            history.setDurationSeconds(durationSeconds);
        }
        history.setCompletionPercentage(completionPercent);
        if (isCompleted || Boolean.TRUE.equals(history.getCompleted())) {
            history.setCompleted(true);
        }
        if (deviceId != null && !deviceId.isBlank()) {
            history.setDeviceId(deviceId);
        }
        if (platform != null) {
            history.setPlatform(platform);
        }
        history.setLastWatchedAt(now);

        WatchHistory saved = watchHistoryRepository.save(history);
        log.debug("Updated watch history: userId={}, contentId={}, watchedSeconds={}, completed={}",
                userId, content.getId(), clampedPosition, saved.getCompleted());

        return saved;
    }

    /**
     * Retrieves paginated watch history for the user, ordered by last watched descending.
     */
    @Transactional(readOnly = true)
    public Page<WatchHistoryResponse> getHistoryForUser(Long userId, Pageable pageable) {
        if (userId == null) {
            return Page.empty(pageable);
        }

        // Limit maximum page size to 50
        int size = Math.min(pageable.getPageSize(), 50);
        int page = Math.max(0, pageable.getPageNumber());

        Pageable boundedPageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "lastWatchedAt"));
        Page<WatchHistory> historyPage = watchHistoryRepository.findByUserIdWithContent(userId, boundedPageable);

        return historyPage.map(WatchHistoryResponse::from);
    }

    /**
     * Deletes a single item from the user's watch history.
     * Operation is idempotent.
     */
    @Transactional
    public boolean deleteHistoryItem(Long userId, Long contentId) {
        if (userId == null || contentId == null) {
            return false;
        }

        int deletedCount = watchHistoryRepository.deleteByUserIdAndContentId(userId, contentId);
        log.info("Deleted watch history item: userId={}, contentId={}, count={}", userId, contentId, deletedCount);
        return deletedCount > 0;
    }

    /**
     * Clears all watch history for the authenticated user.
     */
    @Transactional
    public int clearHistoryForUser(Long userId) {
        if (userId == null) {
            return 0;
        }

        int deletedCount = watchHistoryRepository.deleteAllByUserId(userId);
        log.info("Cleared entire watch history: userId={}, totalCount={}", userId, deletedCount);
        return deletedCount;
    }
}
