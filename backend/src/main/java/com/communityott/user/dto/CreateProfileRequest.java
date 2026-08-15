package com.communityott.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request body for creating an OTT viewing profile")
public class CreateProfileRequest {

    @NotBlank(message = "Profile display name is required")
    @Size(min = 1, max = 100, message = "Profile display name must be between 1 and 100 characters")
    @Schema(description = "Viewing profile name", example = "Hardik")
    private String displayName;

    @Size(max = 500, message = "Avatar URL cannot exceed 500 characters")
    @Schema(description = "Optional avatar image URL", example = "https://cdn.communityott.org/avatars/1.png")
    private String avatarUrl;

    @Size(max = 20, message = "Preferred language code cannot exceed 20 characters")
    @Schema(description = "Preferred language code (e.g. te, en)", example = "te")
    private String preferredLanguage;

    @Schema(description = "Whether this profile should be set as the default profile", example = "false")
    private Boolean isDefault;
}
