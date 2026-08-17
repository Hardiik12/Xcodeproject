package com.communityott.playback.controller;

import com.communityott.common.response.ApiResponse;
import com.communityott.common.security.CommunityOttPrincipal;
import com.communityott.playback.dto.ContinueWatchingItemResponse;
import com.communityott.playback.service.ContinueWatchingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/continue-watching")
@RequiredArgsConstructor
@Tag(name = "Continue Watching API", description = "Endpoints for retrieving in-progress resumable OTT content for user home feed")
@SecurityRequirement(name = "BearerAuth")
@SecurityRequirement(name = "DevUserIdAuth")
public class ContinueWatchingController {

    private final ContinueWatchingService continueWatchingService;

    @GetMapping
    @Operation(summary = "Get Continue Watching list", description = "Returns a paginated list of in-progress, currently playable content items for the authenticated user, ordered by latest viewing activity.")
    public ApiResponse<Page<ContinueWatchingItemResponse>> getContinueWatching(
            @AuthenticationPrincipal CommunityOttPrincipal principal,
            @Parameter(description = "Pagination parameters (default page 0, size 20, max size 50)")
            @PageableDefault(size = 20, sort = "lastWatchedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<ContinueWatchingItemResponse> items = continueWatchingService.getContinueWatching(principal.getUserId(), pageable);
        return ApiResponse.success(items, "Continue watching retrieved successfully");
    }
}
