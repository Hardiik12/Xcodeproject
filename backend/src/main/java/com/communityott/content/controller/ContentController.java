package com.communityott.content.controller;

import com.communityott.common.response.ApiResponse;
import com.communityott.content.dto.ContentFilterCriteria;
import com.communityott.content.dto.ContentResponse;
import com.communityott.content.dto.ContentSummaryResponse;
import com.communityott.content.entity.AgeRating;
import com.communityott.content.entity.ContentType;
import com.communityott.content.service.ContentService;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/content")
@RequiredArgsConstructor
@Tag(name = "Content Catalog API", description = "Public & consumer catalog endpoints for browsing and watching published OTT content")
@SecurityRequirement(name = "BearerAuth")
@SecurityRequirement(name = "DevUserIdAuth")
public class ContentController {

    private final ContentService contentService;
    private final com.communityott.content.service.MediaDeliveryService mediaDeliveryService;

    @GetMapping
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'CONTENT_VIEW')")
    @Operation(summary = "Browse & filter published catalog", description = "Retrieves a paginated, filterable list of published content items. Only content with status PUBLISHED is returned.")
    public ApiResponse<Page<ContentSummaryResponse>> getPublishedCatalog(
            @Parameter(description = "Optional filter by content type (MOVIE, DOCUMENTARY, SERIES, EPISODE)")
            @RequestParam(required = false) ContentType contentType,
            @Parameter(description = "Optional filter by category slug or ID (e.g. 'documentary', 'history')")
            @RequestParam(required = false) String category,
            @Parameter(description = "Optional filter by language code or ID (e.g. 'te', 'en')")
            @RequestParam(required = false) String language,
            @Parameter(description = "Optional filter by age rating (U, UA_7_PLUS, UA_13_PLUS, UA_16_PLUS, A, ALL)")
            @RequestParam(required = false) AgeRating ageRating,
            @Parameter(description = "Optional search keyword across title, subtitle, description, and tags")
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        ContentFilterCriteria criteria = ContentFilterCriteria.builder()
                .contentType(contentType)
                .category(category)
                .language(language)
                .ageRating(ageRating)
                .search(search)
                .build();

        Page<ContentSummaryResponse> catalog = contentService.getPublishedCatalog(criteria, pageable);
        return ApiResponse.success(catalog, "Published catalog retrieved successfully");
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'CONTENT_VIEW')")
    @Operation(summary = "Get published content details", description = "Retrieves full metadata, categories, and languages for a specific published content item. Unpublished content returns 404.")
    public ApiResponse<ContentResponse> getContentDetails(@PathVariable Long id) {
        ContentResponse content = contentService.getPublishedContentById(id);
        return ApiResponse.success(content, "Content details retrieved successfully");
    }

    @GetMapping("/{id}/playback")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'VIDEO_VIEW')")
    @Operation(summary = "Get authorized playback URL", description = "Authorizes consumer playback for published OTT content, generating a secure, time-limited HLS master playlist URL.")
    public ApiResponse<com.communityott.content.dto.PlaybackResponse> getPlaybackInfo(
            @PathVariable Long id,
            org.springframework.security.core.Authentication authentication) {
        String userIdentifier = authentication != null ? authentication.getName() : "anonymous";
        com.communityott.content.dto.PlaybackResponse response = mediaDeliveryService.getPlaybackInfo(id, userIdentifier);
        return ApiResponse.success(response, "Playback authorization granted successfully");
    }

    @GetMapping("/featured")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'CONTENT_VIEW')")
    @Operation(summary = "Get featured content", description = "Retrieves a list of featured published content for top hero carousel banners.")
    public ApiResponse<List<ContentSummaryResponse>> getFeaturedContent() {
        List<ContentSummaryResponse> featured = contentService.getFeaturedContent();
        return ApiResponse.success(featured, "Featured content retrieved successfully");
    }
}
