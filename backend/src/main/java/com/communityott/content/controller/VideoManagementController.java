package com.communityott.content.controller;

import com.communityott.common.security.CommunityOttPrincipal;
import com.communityott.content.dto.VideoAssetResponse;
import com.communityott.content.service.VideoUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/content/{contentId}/videos")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Video Management", description = "Endpoints for video asset upload and storage management")
public class VideoManagementController {

    private final VideoUploadService videoUploadService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'VIDEO_UPLOAD')")
    @Operation(summary = "Upload raw source video asset for content")
    public ResponseEntity<VideoAssetResponse> uploadVideo(
            @PathVariable Long contentId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CommunityOttPrincipal principal) {
        Long userId = principal != null ? principal.getUserId() : null;
        VideoAssetResponse response = videoUploadService.uploadVideo(contentId, file, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'VIDEO_VIEW') or @rbacAuthorization.hasPermission(authentication, 'CONTENT_VIEW')")
    @Operation(summary = "List all video assets for a specific content item")
    public ResponseEntity<List<VideoAssetResponse>> getVideoAssets(
            @PathVariable Long contentId) {
        List<VideoAssetResponse> assets = videoUploadService.getVideoAssetsForContent(contentId);
        return ResponseEntity.ok(assets);
    }

    @GetMapping("/{videoId}")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'VIDEO_VIEW') or @rbacAuthorization.hasPermission(authentication, 'CONTENT_VIEW')")
    @Operation(summary = "Get specific video asset details")
    public ResponseEntity<VideoAssetResponse> getVideoAsset(
            @PathVariable Long contentId,
            @PathVariable Long videoId) {
        VideoAssetResponse response = videoUploadService.getVideoAssetById(contentId, videoId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{videoId}")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'VIDEO_DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete video asset")
    public ResponseEntity<Void> deleteVideoAsset(
            @PathVariable Long contentId,
            @PathVariable Long videoId,
            @AuthenticationPrincipal CommunityOttPrincipal principal) {
        Long userId = principal != null ? principal.getUserId() : null;
        videoUploadService.deleteVideoAsset(contentId, videoId, userId);
        return ResponseEntity.noContent().build();
    }
}
