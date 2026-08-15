package com.communityott.content.controller;

import com.communityott.common.response.ApiResponse;
import com.communityott.common.security.CommunityOttPrincipal;
import com.communityott.common.exception.InvalidJobStateTransitionException;
import com.communityott.content.dto.VideoProcessingJobResponse;
import com.communityott.content.service.VideoProcessingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/videos")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Video Processing Management", description = "Endpoints for managing background video processing jobs")
@SecurityRequirement(name = "bearerAuth")
public class VideoProcessingController {

    private final VideoProcessingService videoProcessingService;

    @PostMapping("/{videoId}/processing")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'VIDEO_PROCESS')")
    @Operation(summary = "Enqueue video probe processing", description = "Creates and enqueues a background PROBE processing job for a video asset")
    public ResponseEntity<ApiResponse<VideoProcessingJobResponse>> enqueueProcessing(
            @PathVariable Long videoId,
            @AuthenticationPrincipal CommunityOttPrincipal principal
    ) {
        Long userId = principal != null ? principal.getUserId() : null;
        log.info("Admin userId={} requesting probe processing for videoId={}", userId, videoId);
        VideoProcessingJobResponse response = videoProcessingService.createAndEnqueueProbeJob(videoId, userId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(response, "Video processing job enqueued successfully"));
    }

    @GetMapping("/{videoId}/processing")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'VIDEO_PROCESS')")
    @Operation(summary = "List video processing jobs", description = "Retrieves all processing jobs associated with a video asset")
    public ResponseEntity<ApiResponse<List<VideoProcessingJobResponse>>> listProcessingJobs(
            @PathVariable Long videoId
    ) {
        log.debug("Fetching processing jobs for videoId={}", videoId);
        List<VideoProcessingJobResponse> response = videoProcessingService.getProcessingJobsForVideo(videoId);
        return ResponseEntity.ok(ApiResponse.success(response, "Processing jobs retrieved successfully"));
    }

    @PostMapping("/{videoId}/processing/retry")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'VIDEO_RETRY')")
    @Operation(summary = "Retry failed video processing job", description = "Requeues a failed video processing job")
    public ResponseEntity<ApiResponse<VideoProcessingJobResponse>> retryProcessing(
            @PathVariable Long videoId,
            @RequestParam(required = false) Long jobId,
            @AuthenticationPrincipal CommunityOttPrincipal principal
    ) {
        Long userId = principal != null ? principal.getUserId() : null;
        log.info("Admin userId={} retrying processing for videoId={}, jobId={}", userId, videoId, jobId);

        Long targetJobId = jobId;
        if (targetJobId == null) {
            // Find the most recent failed job for this video
            List<VideoProcessingJobResponse> jobs = videoProcessingService.getProcessingJobsForVideo(videoId);
            targetJobId = jobs.stream()
                    .filter(j -> j.getStatus() != null && "FAILED".equalsIgnoreCase(j.getStatus().name()))
                    .map(VideoProcessingJobResponse::getId)
                    .findFirst()
                    .orElseThrow(() -> new InvalidJobStateTransitionException("No failed processing job found for video ID " + videoId));
        }

        VideoProcessingJobResponse response = videoProcessingService.retryProcessingJob(targetJobId, userId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(response, "Video processing job retry accepted"));
    }
}
