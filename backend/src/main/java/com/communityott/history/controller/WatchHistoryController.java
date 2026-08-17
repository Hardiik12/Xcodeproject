package com.communityott.history.controller;

import com.communityott.common.response.ApiResponse;
import com.communityott.common.security.CommunityOttPrincipal;
import com.communityott.history.dto.WatchHistoryResponse;
import com.communityott.history.service.WatchHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me/history")
@RequiredArgsConstructor
@Tag(name = "User Watch History API", description = "Endpoints for retrieving, removing, and clearing user OTT watch history")
@SecurityRequirement(name = "BearerAuth")
@SecurityRequirement(name = "DevUserIdAuth")
public class WatchHistoryController {

    private final WatchHistoryService watchHistoryService;

    @GetMapping
    @Operation(summary = "Get user watch history", description = "Retrieves a paginated list of recently watched content items for the authenticated user, ordered newest first.")
    public ApiResponse<Page<WatchHistoryResponse>> getWatchHistory(
            @AuthenticationPrincipal CommunityOttPrincipal principal,
            @Parameter(description = "Pagination parameters (default page 0, size 20, max size 50)")
            @PageableDefault(size = 20, sort = "lastWatchedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<WatchHistoryResponse> historyPage = watchHistoryService.getHistoryForUser(principal.getUserId(), pageable);
        return ApiResponse.success(historyPage, "Watch history retrieved successfully");
    }

    @DeleteMapping("/{contentId}")
    @Operation(summary = "Delete specific watch history item", description = "Removes a specific content item from the authenticated user's watch history. Idempotent.")
    public ApiResponse<Void> deleteHistoryItem(
            @AuthenticationPrincipal CommunityOttPrincipal principal,
            @PathVariable Long contentId) {

        watchHistoryService.deleteHistoryItem(principal.getUserId(), contentId);
        return ApiResponse.success(null, "Watch history item removed successfully");
    }

    @DeleteMapping
    @Operation(summary = "Clear all watch history", description = "Removes all watch history records belonging to the authenticated user account.")
    public ApiResponse<Void> clearAllHistory(
            @AuthenticationPrincipal CommunityOttPrincipal principal) {

        watchHistoryService.clearHistoryForUser(principal.getUserId());
        return ApiResponse.success(null, "Watch history cleared successfully");
    }
}
