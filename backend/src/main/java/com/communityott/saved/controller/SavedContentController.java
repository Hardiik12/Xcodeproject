package com.communityott.saved.controller;

import com.communityott.common.response.ApiResponse;
import com.communityott.common.security.CommunityOttPrincipal;
import com.communityott.saved.dto.SavedContentResponse;
import com.communityott.saved.dto.SavedStatusResponse;
import com.communityott.saved.service.SavedContentService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/my-list")
@RequiredArgsConstructor
@Tag(name = "My List / Saved Content API", description = "Endpoints for managing user's saved content and watchlist")
@SecurityRequirement(name = "BearerAuth")
@SecurityRequirement(name = "DevUserIdAuth")
public class SavedContentController {

    private final SavedContentService savedContentService;

    @PostMapping("/{contentId}")
    @Operation(summary = "Add content to My List", description = "Saves a content item to the authenticated user's My List (idempotent).")
    public ApiResponse<SavedContentResponse> addToMyList(
            @AuthenticationPrincipal CommunityOttPrincipal principal,
            @PathVariable Long contentId) {

        SavedContentResponse response = savedContentService.addToMyList(principal.getUserId(), contentId);
        return ApiResponse.success(response, "Content added to My List successfully");
    }

    @DeleteMapping("/{contentId}")
    @Operation(summary = "Remove content from My List", description = "Removes a content item from the authenticated user's My List (idempotent).")
    public ApiResponse<Void> removeFromMyList(
            @AuthenticationPrincipal CommunityOttPrincipal principal,
            @PathVariable Long contentId) {

        savedContentService.removeFromMyList(principal.getUserId(), contentId);
        return ApiResponse.success(null, "Content removed from My List successfully");
    }

    @GetMapping("/{contentId}")
    @Operation(summary = "Check if content is in My List", description = "Checks whether a given content item is saved in the authenticated user's My List.")
    public ApiResponse<SavedStatusResponse> isSaved(
            @AuthenticationPrincipal CommunityOttPrincipal principal,
            @PathVariable Long contentId) {

        SavedStatusResponse response = savedContentService.isSaved(principal.getUserId(), contentId);
        return ApiResponse.success(response, "Saved status checked successfully");
    }

    @GetMapping
    @Operation(summary = "Get My List", description = "Returns a paginated list of saved content items for the authenticated user, ordered by newest saved first.")
    public ApiResponse<Page<SavedContentResponse>> getMyList(
            @AuthenticationPrincipal CommunityOttPrincipal principal,
            @Parameter(description = "Pagination parameters (default page 0, size 20, max size 50)")
            @PageableDefault(size = 20, sort = "savedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<SavedContentResponse> items = savedContentService.getMyList(principal.getUserId(), pageable);
        return ApiResponse.success(items, "My List retrieved successfully");
    }
}
