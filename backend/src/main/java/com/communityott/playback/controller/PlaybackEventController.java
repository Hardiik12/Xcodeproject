package com.communityott.playback.controller;

import com.communityott.common.security.CommunityOttPrincipal;
import com.communityott.common.response.ApiResponse;
import com.communityott.playback.dto.PlaybackEventBatchRequest;
import com.communityott.playback.dto.PlaybackEventBatchResponse;
import com.communityott.playback.dto.PlaybackEventRequest;
import com.communityott.playback.dto.PlaybackEventResponse;
import com.communityott.playback.service.PlaybackEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/content/{contentId}/playback/sessions/{sessionId}/events")
@RequiredArgsConstructor
@Slf4j
public class PlaybackEventController {

    private final PlaybackEventService playbackEventService;

    /**
     * Ingests a single playback telemetry event for an active session.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<PlaybackEventResponse>> recordEvent(
            @PathVariable Long contentId,
            @PathVariable String sessionId,
            @AuthenticationPrincipal CommunityOttPrincipal principal,
            @Valid @RequestBody PlaybackEventRequest request) {

        log.debug("Received playback event: userId={}, contentId={}, sessionId={}, eventType={}, eventId={}",
                principal.getUserId(), contentId, sessionId, request.getEventType(), request.getEventId());

        PlaybackEventResponse response = playbackEventService.recordEvent(
                principal.getUserId(), contentId, sessionId, request);

        return ResponseEntity.ok(ApiResponse.success(response, "Playback event processed"));
    }

    /**
     * Ingests a batch of playback telemetry events (up to 100).
     */
    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<PlaybackEventBatchResponse>> recordBatch(
            @PathVariable Long contentId,
            @PathVariable String sessionId,
            @AuthenticationPrincipal CommunityOttPrincipal principal,
            @Valid @RequestBody PlaybackEventBatchRequest batchRequest) {

        log.debug("Received playback event batch: userId={}, contentId={}, sessionId={}, size={}",
                principal.getUserId(), contentId, sessionId,
                batchRequest.getEvents() != null ? batchRequest.getEvents().size() : 0);

        PlaybackEventBatchResponse response = playbackEventService.recordBatch(
                principal.getUserId(), contentId, sessionId, batchRequest);

        return ResponseEntity.ok(ApiResponse.success(response, "Playback event batch processed"));
    }
}
