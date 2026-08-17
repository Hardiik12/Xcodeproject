package com.communityott.playback.service;

import com.communityott.auth.entity.Platform;
import com.communityott.common.exception.ContentNotFoundException;
import com.communityott.common.exception.InvalidPlaybackPositionException;
import com.communityott.common.exception.PlaybackSessionAccessDeniedException;
import com.communityott.common.exception.PlaybackSessionExpiredException;
import com.communityott.common.exception.PlaybackSessionNotActiveException;
import com.communityott.common.exception.PlaybackSessionNotFoundException;
import com.communityott.common.exception.VideoNotReadyException;
import com.communityott.content.dto.PlaybackResponse;
import com.communityott.content.entity.Content;
import com.communityott.content.entity.VideoAsset;
import com.communityott.content.entity.VideoAssetStatus;
import com.communityott.content.repository.ContentRepository;
import com.communityott.content.repository.VideoAssetRepository;
import com.communityott.content.service.ContentAccessService;
import com.communityott.content.service.MediaDeliveryService;
import com.communityott.history.service.WatchHistoryService;
import com.communityott.playback.config.PlaybackProperties;
import com.communityott.playback.dto.PlaybackHeartbeatRequest;
import com.communityott.playback.dto.PlaybackProgressRequest;
import com.communityott.playback.dto.PlaybackSessionResponse;
import com.communityott.playback.dto.PlaybackSessionStatusDto;
import com.communityott.playback.dto.StartPlaybackSessionRequest;
import com.communityott.playback.dto.WatchProgressDto;
import com.communityott.playback.entity.PlaybackSession;
import com.communityott.playback.entity.PlaybackSessionStatus;
import com.communityott.playback.entity.WatchProgress;
import com.communityott.playback.repository.PlaybackSessionRepository;
import com.communityott.user.entity.User;
import com.communityott.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PlaybackSessionService {

    private final PlaybackSessionRepository playbackSessionRepository;
    private final ContentRepository contentRepository;
    private final VideoAssetRepository videoAssetRepository;
    private final UserRepository userRepository;
    private final ContentAccessService contentAccessService;
    private final MediaDeliveryService mediaDeliveryService;
    private final WatchProgressService watchProgressService;
    private final WatchHistoryService watchHistoryService;
    private final PlaybackSessionRateLimiter rateLimiter;
    private final PlaybackProperties playbackProperties;

    /**
     * Initializes and starts a new OTT playback session for an authenticated user.
     *
     * @param userId    Authenticated user ID
     * @param contentId Content item ID to watch
     * @param request   Optional client device and platform metadata
     * @return PlaybackSessionResponse with secure playback URL and session token
     */
    @Transactional
    public PlaybackSessionResponse startSession(Long userId, Long contentId, StartPlaybackSessionRequest request) {
        log.info("Starting playback session: userId={}, contentId={}", userId, contentId);

        // 1. Enforce rate limiting on session creation
        rateLimiter.checkSessionCreationRateLimit(userId);

        // 2. Validate Content Existence & Playability
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new ContentNotFoundException(contentId));
        contentAccessService.validateContentPlayable(content);

        // 3. Validate Video Asset Readiness
        List<VideoAsset> assets = videoAssetRepository.findByContentIdOrderByCreatedAtDesc(contentId);
        VideoAsset readyAsset = assets.stream()
                .filter(a -> a.getStatus() == VideoAssetStatus.READY)
                .findFirst()
                .orElseThrow(() -> new VideoNotReadyException("No playable video asset found for content ID: " + contentId));
        contentAccessService.validateVideoAssetPlayable(readyAsset);

        // 4. Authorize and Generate Media Delivery URL & Renditions
        PlaybackResponse playbackInfo = mediaDeliveryService.getPlaybackInfo(contentId, String.valueOf(userId));

        // 5. Look up User's Existing Resume Position
        int resumePosition = watchProgressService.getResumePosition(userId, contentId);

        // 6. Generate Opaque Session Identifier
        String publicSessionId = UUID.randomUUID().toString().replace("-", "");

        // 7. Resolve Metadata
        Platform platform = (request != null && request.getPlatform() != null) ? request.getPlatform() : Platform.WEB;
        String deviceId = (request != null) ? request.getDeviceId() : null;
        int durationSeconds = watchProgressService.resolveDuration(content, readyAsset, playbackInfo.getDurationSeconds());

        User userReference = userRepository.getReferenceById(userId);

        // 8. Persist PlaybackSession
        PlaybackSession session = PlaybackSession.builder()
                .sessionId(publicSessionId)
                .user(userReference)
                .content(content)
                .videoAsset(readyAsset)
                .deviceId(deviceId)
                .platform(platform)
                .status(PlaybackSessionStatus.STARTED)
                .lastPositionSeconds(resumePosition)
                .durationSeconds(durationSeconds)
                .startedAt(Instant.now())
                .lastHeartbeatAt(Instant.now())
                .build();

        playbackSessionRepository.save(session);
        log.info("Created playback session: sessionId={}, userId={}, contentId={}, resumePosition={}s",
                publicSessionId, userId, contentId, resumePosition);

        return PlaybackSessionResponse.builder()
                .playbackSessionId(publicSessionId)
                .contentId(contentId)
                .title(playbackInfo.getTitle())
                .protocol(playbackInfo.getProtocol())
                .playbackUrl(playbackInfo.getPlaybackUrl())
                .startedAt(session.getStartedAt())
                .expiresAt(playbackInfo.getExpiresAt())
                .durationSeconds(durationSeconds)
                .resumePositionSeconds(resumePosition)
                .deliveryMode(playbackInfo.getDeliveryMode())
                .deliveryProvider(playbackInfo.getDeliveryProvider())
                .availableRenditions(playbackInfo.getAvailableRenditions())
                .build();
    }

    /**
     * Records a periodic heartbeat to maintain session liveness and update timestamp/position.
     */
    @Transactional
    public PlaybackSessionStatusDto recordHeartbeat(Long userId, Long contentId, String sessionId, PlaybackHeartbeatRequest request) {
        rateLimiter.checkProgressRateLimit(userId, sessionId);

        PlaybackSession session = findAndValidateSession(sessionId, userId, contentId);
        checkSessionLiveness(session);

        Instant now = Instant.now();
        session.setLastHeartbeatAt(now);

        if (session.getStatus() == PlaybackSessionStatus.STARTED || session.getStatus() == PlaybackSessionStatus.PAUSED) {
            session.setStatus(PlaybackSessionStatus.ACTIVE);
        }

        if (request != null && request.getPositionSeconds() != null) {
            int pos = request.getPositionSeconds();
            if (pos < 0) {
                throw new InvalidPlaybackPositionException("Playback position cannot be negative");
            }
            if (session.getDurationSeconds() > 0 && pos > session.getDurationSeconds() + 10) {
                throw new InvalidPlaybackPositionException(pos, session.getDurationSeconds());
            }
            session.setLastPositionSeconds(pos);
            watchProgressService.recordProgress(userId, session.getContent(), session.getVideoAsset(), pos, session.getDurationSeconds());
            watchHistoryService.recordViewing(userId, session.getContent(), sessionId, pos, session.getDurationSeconds(), session.getDeviceId(), session.getPlatform());
        }

        PlaybackSession saved = playbackSessionRepository.save(session);
        return PlaybackSessionStatusDto.from(saved);
    }

    /**
     * Updates playback progress for an active session and stores durable watch progress.
     */
    @Transactional
    public WatchProgressDto recordProgress(Long userId, Long contentId, String sessionId, PlaybackProgressRequest request) {
        rateLimiter.checkProgressRateLimit(userId, sessionId);

        if (request == null || request.getPositionSeconds() == null) {
            throw new InvalidPlaybackPositionException("Position seconds is required");
        }

        PlaybackSession session = findAndValidateSession(sessionId, userId, contentId);
        checkSessionLiveness(session);

        int pos = request.getPositionSeconds();
        if (pos < 0) {
            throw new InvalidPlaybackPositionException("Playback position cannot be negative");
        }

        int duration = session.getDurationSeconds() > 0 ? session.getDurationSeconds()
                : (request.getDurationSeconds() != null ? request.getDurationSeconds() : 0);

        if (duration > 0 && pos > duration + 10) {
            throw new InvalidPlaybackPositionException(pos, duration);
        }

        session.setLastPositionSeconds(pos);
        session.setLastHeartbeatAt(Instant.now());
        if (session.getStatus() == PlaybackSessionStatus.STARTED || session.getStatus() == PlaybackSessionStatus.PAUSED) {
            session.setStatus(PlaybackSessionStatus.ACTIVE);
        }
        playbackSessionRepository.save(session);

        WatchProgress progress = watchProgressService.recordProgress(
                userId, session.getContent(), session.getVideoAsset(), pos, duration);

        watchHistoryService.recordViewing(userId, session.getContent(), sessionId, pos, duration, session.getDeviceId(), session.getPlatform());

        return WatchProgressDto.from(progress);
    }

    /**
     * Ends a playback session. This operation is idempotent.
     */
    @Transactional
    public PlaybackSessionStatusDto endSession(Long userId, Long contentId, String sessionId, PlaybackProgressRequest request) {
        PlaybackSession session = findAndValidateSession(sessionId, userId, contentId);

        if (session.getStatus() == PlaybackSessionStatus.ENDED) {
            return PlaybackSessionStatusDto.from(session);
        }

        if (request != null && request.getPositionSeconds() != null) {
            int pos = request.getPositionSeconds();
            if (pos >= 0) {
                session.setLastPositionSeconds(pos);
                watchProgressService.recordProgress(userId, session.getContent(), session.getVideoAsset(), pos, session.getDurationSeconds());
                watchHistoryService.recordViewing(userId, session.getContent(), sessionId, pos, session.getDurationSeconds(), session.getDeviceId(), session.getPlatform());
            }
        }

        session.setStatus(PlaybackSessionStatus.ENDED);
        session.setEndedAt(Instant.now());
        PlaybackSession saved = playbackSessionRepository.save(session);
        log.info("Ended playback session: sessionId={}, userId={}, contentId={}", sessionId, userId, contentId);

        return PlaybackSessionStatusDto.from(saved);
    }

    /**
     * Retrieves status of a specific session belonging to the user.
     */
    @Transactional(readOnly = true)
    public PlaybackSessionStatusDto getSession(Long userId, Long contentId, String sessionId) {
        PlaybackSession session = findAndValidateSession(sessionId, userId, contentId);
        return PlaybackSessionStatusDto.from(session);
    }

    /**
     * Validates session ownership, content binding, and existence.
     */
    public PlaybackSession findAndValidateSession(String sessionId, Long userId, Long contentId) {
        PlaybackSession session = playbackSessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new PlaybackSessionNotFoundException(sessionId));

        // Ownership and content binding check
        if (!session.getUser().getId().equals(userId)) {
            log.warn("Unauthorized session access attempt: sessionId={}, ownerId={}, requesterId={}",
                    sessionId, session.getUser().getId(), userId);
            throw new PlaybackSessionAccessDeniedException(sessionId);
        }

        if (!session.getContent().getId().equals(contentId)) {
            log.warn("Session content mismatch: sessionId={}, boundContentId={}, requestedContentId={}",
                    sessionId, session.getContent().getId(), contentId);
            throw new PlaybackSessionNotFoundException("Playback session does not belong to content ID: " + contentId);
        }

        return session;
    }

    /**
     * Checks if the session is alive (not expired by timeout or closed).
     */
    private void checkSessionLiveness(PlaybackSession session) {
        if (session.getStatus() == PlaybackSessionStatus.ENDED) {
            throw new PlaybackSessionNotActiveException(session.getSessionId(), session.getStatus().name());
        }

        if (session.getStatus() == PlaybackSessionStatus.EXPIRED) {
            throw new PlaybackSessionExpiredException(session.getSessionId());
        }

        long timeoutMinutes = playbackProperties.getSessionInactivityTimeoutMinutes();
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(timeoutMinutes));

        if (session.getLastHeartbeatAt().isBefore(cutoff)) {
            log.info("Marking playback session as EXPIRED due to inactivity: sessionId={}", session.getSessionId());
            session.setStatus(PlaybackSessionStatus.EXPIRED);
            playbackSessionRepository.save(session);
            throw new PlaybackSessionExpiredException(session.getSessionId());
        }
    }

    /**
     * Periodic background job to reconcile and mark stale inactive sessions as EXPIRED.
     */
    @Scheduled(fixedDelayString = "${communityott.playback.cleanup-interval-ms:60000}")
    @Transactional
    public void expireInactiveSessions() {
        long timeoutMinutes = playbackProperties.getSessionInactivityTimeoutMinutes();
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(timeoutMinutes));

        List<PlaybackSession> activeStale = playbackSessionRepository
                .findByStatusAndLastHeartbeatAtBefore(PlaybackSessionStatus.ACTIVE, cutoff);
        List<PlaybackSession> startedStale = playbackSessionRepository
                .findByStatusAndLastHeartbeatAtBefore(PlaybackSessionStatus.STARTED, cutoff);
        List<PlaybackSession> pausedStale = playbackSessionRepository
                .findByStatusAndLastHeartbeatAtBefore(PlaybackSessionStatus.PAUSED, cutoff);

        for (PlaybackSession s : activeStale) {
            s.setStatus(PlaybackSessionStatus.EXPIRED);
        }
        for (PlaybackSession s : startedStale) {
            s.setStatus(PlaybackSessionStatus.EXPIRED);
        }
        for (PlaybackSession s : pausedStale) {
            s.setStatus(PlaybackSessionStatus.EXPIRED);
        }

        if (!activeStale.isEmpty() || !startedStale.isEmpty() || !pausedStale.isEmpty()) {
            int total = activeStale.size() + startedStale.size() + pausedStale.size();
            log.info("Reconciled and expired {} inactive playback sessions", total);
        }
    }
}
