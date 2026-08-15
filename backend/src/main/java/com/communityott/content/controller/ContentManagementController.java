package com.communityott.content.controller;

import com.communityott.common.response.ApiResponse;
import com.communityott.common.security.CommunityOttPrincipal;
import com.communityott.content.dto.*;
import com.communityott.content.entity.AgeRating;
import com.communityott.content.entity.ContentStatus;
import com.communityott.content.entity.ContentType;
import com.communityott.content.service.ContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/content")
@RequiredArgsConstructor
@Tag(name = "Admin Content Management API", description = "Administrative endpoints for creating, managing, and publishing OTT content")
@SecurityRequirement(name = "BearerAuth")
@SecurityRequirement(name = "DevUserIdAuth")
public class ContentManagementController {

    private final ContentService contentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'CONTENT_CREATE')")
    @Operation(summary = "Create new content item", description = "Creates a new content item in DRAFT status. Requires CONTENT_CREATE permission.")
    public ApiResponse<ContentResponse> createContent(
            @AuthenticationPrincipal CommunityOttPrincipal principal,
            @Valid @RequestBody CreateContentRequest request) {

        ContentResponse response = contentService.createContent(request, principal.getUserId());
        return ApiResponse.success(response, "Content created successfully in DRAFT status");
    }

    @GetMapping
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'CONTENT_VIEW')")
    @Operation(summary = "List all content items (Admin)", description = "Retrieves all content items regardless of status for administrative oversight.")
    public ApiResponse<Page<ContentResponse>> listContent(
            @Parameter(description = "Optional filter by status")
            @RequestParam(required = false) ContentStatus status,
            @Parameter(description = "Optional filter by content type")
            @RequestParam(required = false) ContentType contentType,
            @Parameter(description = "Optional filter by category slug or ID")
            @RequestParam(required = false) String category,
            @Parameter(description = "Optional filter by language code or ID")
            @RequestParam(required = false) String language,
            @Parameter(description = "Optional filter by age rating")
            @RequestParam(required = false) AgeRating ageRating,
            @Parameter(description = "Optional search query")
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        ContentFilterCriteria criteria = ContentFilterCriteria.builder()
                .status(status)
                .contentType(contentType)
                .category(category)
                .language(language)
                .ageRating(ageRating)
                .search(search)
                .build();

        Page<ContentResponse> page = contentService.listContentForAdmin(criteria, pageable);
        return ApiResponse.success(page, "Content list retrieved successfully");
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'CONTENT_VIEW')")
    @Operation(summary = "Get content item details (Admin)", description = "Retrieves content item details regardless of publication status.")
    public ApiResponse<ContentResponse> getContent(@PathVariable Long id) {
        ContentResponse content = contentService.getContentForAdmin(id);
        return ApiResponse.success(content, "Content details retrieved successfully");
    }

    @PutMapping("/{id}")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'CONTENT_UPDATE')")
    @Operation(summary = "Update content item", description = "Updates metadata of an existing content item. Requires CONTENT_UPDATE permission.")
    public ApiResponse<ContentResponse> updateContent(
            @AuthenticationPrincipal CommunityOttPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateContentRequest request) {

        ContentResponse updated = contentService.updateContent(id, request, principal.getUserId());
        return ApiResponse.success(updated, "Content updated successfully");
    }

    @PutMapping("/{id}/metadata")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'CONTENT_METADATA_UPDATE') or @rbacAuthorization.hasPermission(authentication, 'CONTENT_UPDATE')")
    @Operation(summary = "Update content taxonomy metadata", description = "Updates subtitle, short description, tags, categories, and languages. Requires CONTENT_METADATA_UPDATE or CONTENT_UPDATE permission.")
    public ApiResponse<ContentResponse> updateMetadata(
            @AuthenticationPrincipal CommunityOttPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody ContentMetadataUpdateRequest request) {

        ContentResponse updated = contentService.updateMetadata(id, request, principal.getUserId());
        return ApiResponse.success(updated, "Content metadata updated successfully");
    }

    @PostMapping("/{id}/categories/{categoryId}")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'CONTENT_METADATA_UPDATE') or @rbacAuthorization.hasPermission(authentication, 'CONTENT_UPDATE')")
    @Operation(summary = "Assign category to content", description = "Associates a category with a content item.")
    public ApiResponse<ContentResponse> assignCategory(
            @AuthenticationPrincipal CommunityOttPrincipal principal,
            @PathVariable Long id,
            @PathVariable Long categoryId) {

        ContentResponse updated = contentService.assignCategory(id, categoryId, principal.getUserId());
        return ApiResponse.success(updated, "Category assigned successfully");
    }

    @DeleteMapping("/{id}/categories/{categoryId}")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'CONTENT_METADATA_UPDATE') or @rbacAuthorization.hasPermission(authentication, 'CONTENT_UPDATE')")
    @Operation(summary = "Remove category from content", description = "Disassociates a category from a content item.")
    public ApiResponse<ContentResponse> removeCategory(
            @AuthenticationPrincipal CommunityOttPrincipal principal,
            @PathVariable Long id,
            @PathVariable Long categoryId) {

        ContentResponse updated = contentService.removeCategory(id, categoryId, principal.getUserId());
        return ApiResponse.success(updated, "Category removed successfully");
    }

    @PostMapping("/{id}/languages/{languageId}")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'CONTENT_METADATA_UPDATE') or @rbacAuthorization.hasPermission(authentication, 'CONTENT_UPDATE')")
    @Operation(summary = "Assign language to content", description = "Associates an available language with a content item.")
    public ApiResponse<ContentResponse> assignLanguage(
            @AuthenticationPrincipal CommunityOttPrincipal principal,
            @PathVariable Long id,
            @PathVariable Long languageId) {

        ContentResponse updated = contentService.assignLanguage(id, languageId, principal.getUserId());
        return ApiResponse.success(updated, "Language assigned successfully");
    }

    @DeleteMapping("/{id}/languages/{languageId}")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'CONTENT_METADATA_UPDATE') or @rbacAuthorization.hasPermission(authentication, 'CONTENT_UPDATE')")
    @Operation(summary = "Remove language from content", description = "Disassociates an available language from a content item.")
    public ApiResponse<ContentResponse> removeLanguage(
            @AuthenticationPrincipal CommunityOttPrincipal principal,
            @PathVariable Long id,
            @PathVariable Long languageId) {

        ContentResponse updated = contentService.removeLanguage(id, languageId, principal.getUserId());
        return ApiResponse.success(updated, "Language removed successfully");
    }

    @GetMapping("/summary")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'CONTENT_VIEW')")
    @Operation(summary = "Get content catalog status summary breakdown", description = "Returns total count and count per lifecycle state (draft, uploading, processing, ready, published, unpublished, failed, archived). Requires CONTENT_VIEW permission.")
    public ApiResponse<ContentStatusSummaryResponse> getContentStatusSummary() {
        ContentStatusSummaryResponse summary = contentService.getContentStatusSummary();
        return ApiResponse.success(summary, "Content status summary retrieved successfully");
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'CONTENT_PUBLISH')")
    @Operation(summary = "Publish content item", description = "Validates publishability prerequisites and transitions status to PUBLISHED. Requires CONTENT_PUBLISH permission.")
    public ApiResponse<ContentResponse> publishContent(
            @AuthenticationPrincipal CommunityOttPrincipal principal,
            @PathVariable Long id) {

        ContentResponse published = contentService.publishContent(id, principal.getUserId());
        return ApiResponse.success(published, "Content published successfully");
    }

    @PostMapping("/{id}/unpublish")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'CONTENT_PUBLISH')")
    @Operation(summary = "Unpublish content item", description = "Transitions status from PUBLISHED to UNPUBLISHED, immediately hiding it from consumer feeds. Requires CONTENT_PUBLISH permission.")
    public ApiResponse<ContentResponse> unpublishContent(
            @AuthenticationPrincipal CommunityOttPrincipal principal,
            @PathVariable Long id) {

        ContentResponse unpublished = contentService.unpublishContent(id, principal.getUserId());
        return ApiResponse.success(unpublished, "Content unpublished successfully");
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'CONTENT_ARCHIVE') or @rbacAuthorization.hasPermission(authentication, 'CONTENT_DELETE')")
    @Operation(summary = "Archive content item", description = "Transitions status to ARCHIVED, preserving historical referential integrity. Requires CONTENT_ARCHIVE or CONTENT_DELETE permission.")
    public ApiResponse<ContentResponse> archiveContentPost(
            @AuthenticationPrincipal CommunityOttPrincipal principal,
            @PathVariable Long id) {

        ContentResponse archived = contentService.archiveContent(id, principal.getUserId());
        return ApiResponse.success(archived, "Content archived successfully");
    }

    @PostMapping("/{id}/retry-processing")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'VIDEO_RETRY') or @rbacAuthorization.hasPermission(authentication, 'CONTENT_UPDATE')")
    @Operation(summary = "Retry failed video processing", description = "Transitions status from FAILED back to PROCESSING. Requires VIDEO_RETRY or CONTENT_UPDATE permission.")
    public ApiResponse<ContentResponse> retryProcessing(
            @AuthenticationPrincipal CommunityOttPrincipal principal,
            @PathVariable Long id) {

        ContentResponse retried = contentService.retryProcessing(id, principal.getUserId());
        return ApiResponse.success(retried, "Content processing retry initiated successfully");
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'CONTENT_UPDATE') or @rbacAuthorization.hasPermission(authentication, 'CONTENT_PUBLISH')")
    @Operation(summary = "Generic content lifecycle state transition", description = "Applies a state machine transition to targetStatus with validation.")
    public ApiResponse<ContentResponse> transitionStatus(
            @AuthenticationPrincipal CommunityOttPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody ContentStatusTransitionRequest request) {

        ContentResponse transitioned = contentService.transitionStatus(id, request.getTargetStatus(), principal.getUserId());
        return ApiResponse.success(transitioned, "Content state transitioned successfully");
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'CONTENT_PUBLISH') or @rbacAuthorization.hasPermission(authentication, 'CONTENT_UPDATE')")
    @Operation(summary = "Transition content lifecycle status (legacy)", description = "Transitions content status. Requires CONTENT_PUBLISH or CONTENT_UPDATE permission.")
    public ApiResponse<ContentResponse> updateStatus(
            @AuthenticationPrincipal CommunityOttPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateContentStatusRequest request) {

        ContentResponse updated = contentService.transitionStatus(id, request.getStatus(), principal.getUserId());
        return ApiResponse.success(updated, "Content status updated successfully");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'CONTENT_DELETE') or @rbacAuthorization.hasPermission(authentication, 'CONTENT_ARCHIVE')")
    @Operation(summary = "Archive content item (DELETE mapping)", description = "Logically archives a content item. Requires CONTENT_DELETE or CONTENT_ARCHIVE permission.")
    public ApiResponse<ContentResponse> archiveContent(
            @AuthenticationPrincipal CommunityOttPrincipal principal,
            @PathVariable Long id) {

        ContentResponse archived = contentService.archiveContent(id, principal.getUserId());
        return ApiResponse.success(archived, "Content archived successfully");
    }
}
