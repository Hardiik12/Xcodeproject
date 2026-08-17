package com.communityott.playback.controller;

import com.communityott.common.response.ApiResponse;
import com.communityott.common.security.CommunityOttPrincipal;
import com.communityott.playback.dto.PlaybackHeartbeatRequest;
import com.communityott.playback.dto.PlaybackProgressRequest;
import com.communityott.playback.dto.PlaybackSessionResponse;
import com.communityott.playback.dto.PlaybackSessionStatusDto;
import com.communityott.playback.dto.StartPlaybackSessionRequest;
import com.communityott.playback.dto.WatchProgressDto;
import com.communityott.playback.service.PlaybackSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/content/{contentId}/playback/sessions")
@RequiredArgsConstructor
@Tag(name = "Playback Sessions API", description = "Endpoints for managing video playback session lifecycle and watch progress")
@SecurityRequirement(name = "BearerAuth")
@SecurityRequirement(name = "DevUserIdAuth")
public class PlaybackSessionController {

    private final PlaybackSessionService playbackSessionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'VIDEO_VIEW')")
    @Operation(summary = "Start playback session", description = "Authorizes and starts a playback session, returning a secure playback URL and resume position.")
    public ApiResponse<PlaybackSessionResponse> startSession(
            @AuthenticationPrincipal CommunityOttPrincipal principal,
            @PathVariable Long contentId,
            @Valid @RequestBody(required = false) StartPlaybackSessionRequest request) {

        PlaybackSessionResponse response = playbackSessionService.startSession(principal.getUserId(), contentId, request);
        return ApiResponse.success(response, "Playback session started successfully");
    }

    @PostMapping("/{sessionId}/heartbeat")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'VIDEO_VIEW')")
    @Operation(summary = "Record session heartbeat", description = "Sends periodic heartbeat to maintain session liveness and optionally updates position.")
    public ApiResponse<PlaybackSessionStatusDto> recordHeartbeat(
            @AuthenticationPrincipal CommunityOttPrincipal principal,
            @PathVariable Long contentId,
            @PathVariable String sessionId,
            @Valid @RequestBody(required = false) PlaybackHeartbeatRequest request) {

        PlaybackSessionStatusDto response = playbackSessionService.recordHeartbeat(principal.getUserId(), contentId, sessionId, request);
        return ApiResponse.success(response, "Playback heartbeat recorded successfully");
    }

    @PostMapping("/{sessionId}/progress")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'VIDEO_VIEW')")
    @Operation(summary = "Update watch progress", description = "Updates current watch progress and completion status for the playback session.")
    public ApiResponse<WatchProgressDto> recordProgress(
            @AuthenticationPrincipal CommunityOttPrincipal principal,
            @PathVariable Long contentId,
            @PathVariable String sessionId,
            @Valid @RequestBody PlaybackProgressRequest request) {

        WatchProgressDto response = playbackSessionService.recordProgress(principal.getUserId(), contentId, sessionId, request);
        return ApiResponse.success(response, "Watch progress updated successfully");
    }

    @PostMapping("/{sessionId}/end")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'VIDEO_VIEW')")
    @Operation(summary = "End playback session", description = "Gracefully closes the playback session and commits the final watch progress.")
    public ApiResponse<PlaybackSessionStatusDto> endSession(
            @AuthenticationPrincipal CommunityOttPrincipal principal,
            @PathVariable Long contentId,
            @PathVariable String sessionId,
            @Valid @RequestBody(required = false) PlaybackProgressRequest request) {

        PlaybackSessionStatusDto response = playbackSessionService.endSession(principal.getUserId(), contentId, sessionId, request);
        return ApiResponse.success(response, "Playback session ended successfully");
    }

    @GetMapping("/{sessionId}")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'VIDEO_VIEW')")
    @Operation(summary = "Get playback session status", description = "Retrieves current status and playback state for an active or past session.")
    public ApiResponse<PlaybackSessionStatusDto> getSession(
            @AuthenticationPrincipal CommunityOttPrincipal principal,
            @PathVariable Long contentId,
            @PathVariable String sessionId) {

        PlaybackSessionStatusDto response = playbackSessionService.getSession(principal.getUserId(), contentId, sessionId);
        return ApiResponse.success(response, "Playback session retrieved successfully");
    }
}
