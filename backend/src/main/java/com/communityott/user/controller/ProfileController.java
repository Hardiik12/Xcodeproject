package com.communityott.user.controller;

import com.communityott.common.response.ApiResponse;
import com.communityott.common.security.CommunityOttPrincipal;
import com.communityott.user.dto.CreateProfileRequest;
import com.communityott.user.dto.ProfileResponse;
import com.communityott.user.dto.UpdateProfileRequest;
import com.communityott.user.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/profiles")
@RequiredArgsConstructor
@Tag(name = "User Profiles API", description = "Endpoints for managing user OTT viewing profiles")
@SecurityRequirement(name = "BearerAuth")
@SecurityRequirement(name = "DevUserIdAuth")
public class ProfileController {

    private final ProfileService profileService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new viewing profile", description = "Creates a new OTT viewing persona for the authenticated user account.")
    public ApiResponse<ProfileResponse> createProfile(
            @AuthenticationPrincipal CommunityOttPrincipal principal,
            @Valid @RequestBody CreateProfileRequest request) {

        ProfileResponse response = profileService.createProfile(principal.getUserId(), request);
        return ApiResponse.success(response, "Profile created successfully");
    }

    @GetMapping
    @Operation(summary = "List viewing profiles", description = "Returns all viewing profiles associated with the authenticated user.")
    public ApiResponse<List<ProfileResponse>> listProfiles(
            @AuthenticationPrincipal CommunityOttPrincipal principal) {

        List<ProfileResponse> profiles = profileService.getProfilesForUser(principal.getUserId());
        return ApiResponse.success(profiles, "Profiles retrieved successfully");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get profile by ID", description = "Retrieves a specific viewing profile belonging to the authenticated user.")
    public ApiResponse<ProfileResponse> getProfile(
            @AuthenticationPrincipal CommunityOttPrincipal principal,
            @PathVariable Long id) {

        ProfileResponse profile = profileService.getProfileByIdAndUser(id, principal.getUserId());
        return ApiResponse.success(profile, "Profile retrieved successfully");
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update profile", description = "Updates details of a viewing profile belonging to the authenticated user.")
    public ApiResponse<ProfileResponse> updateProfile(
            @AuthenticationPrincipal CommunityOttPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateProfileRequest request) {

        ProfileResponse updatedProfile = profileService.updateProfile(id, principal.getUserId(), request);
        return ApiResponse.success(updatedProfile, "Profile updated successfully");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete profile", description = "Deletes a viewing profile belonging to the authenticated user.")
    public ApiResponse<Void> deleteProfile(
            @AuthenticationPrincipal CommunityOttPrincipal principal,
            @PathVariable Long id) {

        profileService.deleteProfile(id, principal.getUserId());
        return ApiResponse.success(null, "Profile deleted successfully");
    }
}
