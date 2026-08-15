package com.communityott.content.controller;

import com.communityott.common.response.ApiResponse;
import com.communityott.common.security.CommunityOttPrincipal;
import com.communityott.content.dto.LanguageCreateRequest;
import com.communityott.content.dto.LanguageResponse;
import com.communityott.content.dto.LanguageUpdateRequest;
import com.communityott.content.service.LanguageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/languages")
@RequiredArgsConstructor
@Tag(name = "Admin Language Management API", description = "Administrative endpoints for managing platform languages")
@SecurityRequirement(name = "BearerAuth")
@SecurityRequirement(name = "DevUserIdAuth")
public class LanguageManagementController {

    private final LanguageService languageService;

    @GetMapping
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'LANGUAGE_VIEW')")
    @Operation(summary = "List all languages (Admin)", description = "Retrieves all languages including inactive ones.")
    public ApiResponse<List<LanguageResponse>> getAllLanguages() {
        List<LanguageResponse> languages = languageService.getAllLanguagesForAdmin();
        return ApiResponse.success(languages, "All languages retrieved successfully");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'LANGUAGE_CREATE')")
    @Operation(summary = "Create language", description = "Creates a new language. Requires LANGUAGE_CREATE permission.")
    public ApiResponse<LanguageResponse> createLanguage(
            @AuthenticationPrincipal CommunityOttPrincipal principal,
            @Valid @RequestBody LanguageCreateRequest request) {

        LanguageResponse language = languageService.createLanguage(request, principal.getUserId());
        return ApiResponse.success(language, "Language created successfully");
    }

    @PutMapping("/{id}")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'LANGUAGE_UPDATE')")
    @Operation(summary = "Update language", description = "Updates an existing language. Requires LANGUAGE_UPDATE permission.")
    public ApiResponse<LanguageResponse> updateLanguage(
            @AuthenticationPrincipal CommunityOttPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody LanguageUpdateRequest request) {

        LanguageResponse language = languageService.updateLanguage(id, request, principal.getUserId());
        return ApiResponse.success(language, "Language updated successfully");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'LANGUAGE_DELETE')")
    @Operation(summary = "Deactivate language", description = "Deactivates a language (soft-delete / active=false) safely without breaking content associations.")
    public ApiResponse<LanguageResponse> deactivateLanguage(
            @AuthenticationPrincipal CommunityOttPrincipal principal,
            @PathVariable Long id) {

        LanguageResponse language = languageService.deactivateLanguage(id, principal.getUserId());
        return ApiResponse.success(language, "Language deactivated successfully");
    }
}
