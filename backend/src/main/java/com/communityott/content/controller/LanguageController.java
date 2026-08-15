package com.communityott.content.controller;

import com.communityott.common.response.ApiResponse;
import com.communityott.content.dto.LanguageResponse;
import com.communityott.content.service.LanguageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/languages")
@RequiredArgsConstructor
@Tag(name = "Language Discovery API", description = "Public & consumer endpoints for retrieving active platform languages")
@SecurityRequirement(name = "BearerAuth")
@SecurityRequirement(name = "DevUserIdAuth")
public class LanguageController {

    private final LanguageService languageService;

    @GetMapping
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'LANGUAGE_VIEW')")
    @Operation(summary = "List active languages", description = "Retrieves all active languages available on the platform.")
    public ApiResponse<List<LanguageResponse>> getActiveLanguages() {
        List<LanguageResponse> languages = languageService.getActiveLanguages();
        return ApiResponse.success(languages, "Active languages retrieved successfully");
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'LANGUAGE_VIEW')")
    @Operation(summary = "Get language by ID", description = "Retrieves details for a specific language.")
    public ApiResponse<LanguageResponse> getLanguageById(@PathVariable Long id) {
        LanguageResponse language = languageService.getLanguageById(id);
        return ApiResponse.success(language, "Language details retrieved successfully");
    }
}
