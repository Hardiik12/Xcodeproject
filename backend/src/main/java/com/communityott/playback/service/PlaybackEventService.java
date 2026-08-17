package com.communityott.playback.service;

import com.communityott.common.exception.ApiException;
import com.communityott.common.exception.InvalidPlaybackPositionException;
import com.communityott.common.exception.PlaybackSessionNotActiveException;
import com.communityott.history.service.WatchHistoryService;
import com.communityott.playback.dto.PlaybackEventBatchRequest;
import com.communityott.playback.dto.PlaybackEventBatchResponse;
import com.communityott.playback.dto.PlaybackEventRequest;
import com.communityott.playback.dto.PlaybackEventResponse;
import com.communityott.playback.dto.PlaybackProgressRequest;
import com.communityott.playback.entity.PlaybackEvent;
import com.communityott.playback.entity.PlaybackEventType;
import com.communityott.playback.entity.PlaybackSession;
import com.communityott.playback.entity.PlaybackSessionStatus;
import com.communityott.playback.repository.PlaybackEventRepository;
import com.communityott.playback.repository.PlaybackSessionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class PlaybackEventService {

    private final PlaybackEventRepository playbackEventRepository;
    private final PlaybackSessionRepository playbackSessionRepository;
    private final PlaybackSessionService playbackSessionService;
    private final WatchProgressService watchProgressService;
    private final WatchHistoryService watchHistoryService;
    private final PlaybackSessionRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    private static final int MAX_METADATA_LENGTH = 4000;

    /**
     * Ingests a single playback telemetry event for an active session.
     */
    @Transactional
    public PlaybackEventResponse recordEvent(Long userId, Long contentId, String sessionId, PlaybackEventRequest request) {
        if (request == null || request.getEventId() == null || request.getEventType() == null) {
            throw new ApiException("Event ID and type are required", HttpStatus.BAD_REQUEST, "INVALID_PLAYBACK_EVENT");
        }

        // 1. Rate Limit Ingestion
        rateLimiter.checkProgressRateLimit(userId, sessionId);

        // 2. Validate Session Ownership and Liveness
        PlaybackSession session = playbackSessionService.findAndValidateSession(sessionId, userId, contentId);
        playbackSessionService.checkSessionLiveness(session);

        // Disallow events on already ended sessions unless it's a duplicate END or error
        if (session.getStatus() == PlaybackSessionStatus.ENDED && request.getEventType() != PlaybackEventType.END) {
            throw new PlaybackSessionNotActiveException(sessionId, session.getStatus().name());
        }

        // 3. Check for Duplicate Event ID (Idempotency)
        if (playbackEventRepository.existsByEventId(request.getEventId())) {
            log.info("Duplicate playback event ignored: eventId={}, sessionId={}", request.getEventId(), sessionId);
            return PlaybackEventResponse.duplicate(request.getEventId());
        }

        // 4. Validate Position and Duration
        int pos = request.getPositionSeconds();
        if (pos < 0) {
            throw new InvalidPlaybackPositionException("Playback position cannot be negative");
        }

        int duration = session.getDurationSeconds() > 0 ? session.getDurationSeconds()
                : (request.getDurationSeconds() != null ? request.getDurationSeconds() : 0);

        if (duration > 0 && pos > duration + 10) {
            throw new InvalidPlaybackPositionException(pos, duration);
        }

        // 5. Serialize and Validate Metadata Payload Size
        String metadataJson = null;
        if (request.getMetadata() != null && !request.getMetadata().isEmpty()) {
            try {
                metadataJson = objectMapper.writeValueAsString(request.getMetadata());
                if (metadataJson.length() > MAX_METADATA_LENGTH) {
                    throw new ApiException("Metadata payload exceeds allowed 4KB size limit",
                            HttpStatus.BAD_REQUEST, "EVENT_PAYLOAD_TOO_LARGE");
                }
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize telemetry metadata for eventId={}: {}", request.getEventId(), e.getMessage());
            }
        }

        Instant occurredAt = request.getOccurredAt() != null ? request.getOccurredAt() : Instant.now();
        Instant receivedAt = Instant.now();

        // 6. Build and Persist PlaybackEvent
        PlaybackEvent event = PlaybackEvent.builder()
                .eventId(request.getEventId())
                .playbackSession(session)
                .user(session.getUser())
                .content(session.getContent())
                .videoAsset(session.getVideoAsset())
                .eventType(request.getEventType())
                .positionSeconds(pos)
                .durationSeconds(duration)
                .occurredAt(occurredAt)
                .receivedAt(receivedAt)
                .platform(session.getPlatform())
                .deviceId(session.getDeviceId())
                .sessionSequence(request.getSequence())
                .metadata(metadataJson)
                .build();

        try {
            playbackEventRepository.save(event);
        } catch (DataIntegrityViolationException e) {
            log.info("Database unique constraint caught duplicate event: eventId={}", request.getEventId());
            return PlaybackEventResponse.duplicate(request.getEventId());
        }

        // 7. Synchronize Session State & Progress / History
        applyEventToSessionAndProgress(session, request.getEventType(), pos, duration, userId, contentId, sessionId);

        return PlaybackEventResponse.accepted(request.getEventId(), receivedAt);
    }

    /**
     * Ingests a batch of telemetry events for an active session.
     */
    @Transactional
    public PlaybackEventBatchResponse recordBatch(Long userId, Long contentId, String sessionId, PlaybackEventBatchRequest batchRequest) {
        if (batchRequest == null || batchRequest.getEvents() == null || batchRequest.getEvents().isEmpty()) {
            throw new ApiException("Events batch cannot be empty", HttpStatus.BAD_REQUEST, "INVALID_PLAYBACK_EVENT");
        }

        if (batchRequest.getEvents().size() > 100) {
            throw new ApiException("Batch size exceeds maximum allowed 100 events", HttpStatus.BAD_REQUEST, "EVENT_PAYLOAD_TOO_LARGE");
        }

        // Validate session once upfront
        PlaybackSession session = playbackSessionService.findAndValidateSession(sessionId, userId, contentId);
        playbackSessionService.checkSessionLiveness(session);

        List<PlaybackEventRequest> events = new ArrayList<>(batchRequest.getEvents());
        // Sort events by sequence if provided, otherwise by occurredAt
        events.sort(Comparator.comparing(
                e -> e.getSequence() != null ? e.getSequence() : 0
        ));

        int accepted = 0;
        int duplicate = 0;
        int rejected = 0;
        List<PlaybackEventResponse> results = new ArrayList<>();

        for (PlaybackEventRequest eventReq : events) {
            try {
                PlaybackEventResponse response = recordEvent(userId, contentId, sessionId, eventReq);
                if (response.isAccepted()) {
                    if ("Event already processed (idempotent)".equals(response.getMessage())) {
                        duplicate++;
                    } else {
                        accepted++;
                    }
                } else {
                    rejected++;
                }
                results.add(response);
            } catch (Exception ex) {
                log.warn("Error processing eventId={} in batch: {}", eventReq.getEventId(), ex.getMessage());
                rejected++;
                results.add(PlaybackEventResponse.rejected(eventReq.getEventId(), ex.getMessage()));
            }
        }

        return PlaybackEventBatchResponse.builder()
                .totalSubmitted(events.size())
                .acceptedCount(accepted)
                .duplicateCount(duplicate)
                .rejectedCount(rejected)
                .results(results)
                .build();
    }

    /**
     * Applies side effects of playback events to session status, watch progress, and watch history.
     */
    private void applyEventToSessionAndProgress(PlaybackSession session, PlaybackEventType eventType,
                                                int pos, int duration, Long userId, Long contentId, String sessionId) {
        switch (eventType) {
            case PLAY:
            case RESUME:
            case BUFFER_END:
                if (session.getStatus() == PlaybackSessionStatus.STARTED || session.getStatus() == PlaybackSessionStatus.PAUSED) {
                    session.setStatus(PlaybackSessionStatus.ACTIVE);
                }
                session.setLastPositionSeconds(pos);
                session.setLastHeartbeatAt(Instant.now());
                playbackSessionRepository.save(session);
                watchProgressService.recordProgress(userId, session.getContent(), session.getVideoAsset(), pos, duration);
                watchHistoryService.recordViewing(userId, session.getContent(), sessionId, pos, duration, session.getDeviceId(), session.getPlatform());
                break;

            case PAUSE:
                if (session.getStatus() == PlaybackSessionStatus.ACTIVE || session.getStatus() == PlaybackSessionStatus.STARTED) {
                    session.setStatus(PlaybackSessionStatus.PAUSED);
                }
                session.setLastPositionSeconds(pos);
                session.setLastHeartbeatAt(Instant.now());
                playbackSessionRepository.save(session);
                watchProgressService.recordProgress(userId, session.getContent(), session.getVideoAsset(), pos, duration);
                watchHistoryService.recordViewing(userId, session.getContent(), sessionId, pos, duration, session.getDeviceId(), session.getPlatform());
                break;

            case HEARTBEAT:
            case SEEK:
            case COMPLETE:
                session.setLastPositionSeconds(pos);
                session.setLastHeartbeatAt(Instant.now());
                playbackSessionRepository.save(session);
                watchProgressService.recordProgress(userId, session.getContent(), session.getVideoAsset(), pos, duration);
                watchHistoryService.recordViewing(userId, session.getContent(), sessionId, pos, duration, session.getDeviceId(), session.getPlatform());
                break;

            case END:
                playbackSessionService.endSession(userId, contentId, sessionId,
                        new PlaybackProgressRequest(pos, duration));
                break;

            case BUFFER_START:
            case QUALITY_CHANGE:
            case ERROR:
            default:
                // Telemetry recorded, no state machine transition needed
                break;
        }
    }
}
