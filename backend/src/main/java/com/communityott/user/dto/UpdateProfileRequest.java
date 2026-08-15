package com.communityott.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request body for updating an OTT viewing profile")
public class UpdateProfileRequest {

    @Size(min = 1, max = 100, message = "Profile display name must be between 1 and 100 characters")
    @Schema(description = "Updated viewing profile name", example = "Hardik P")
    private String displayName;

    @Size(max = 500, message = "Avatar URL cannot exceed 500 characters")
    @Schema(description = "Updated avatar image URL", example = "https://cdn.communityott.org/avatars/2.png")
    private String avatarUrl;

    @Size(max = 20, message = "Preferred language code cannot exceed 20 characters")
    @Schema(description = "Updated preferred language code (e.g. te, en)", example = "en")
    private String preferredLanguage;

    @Schema(description = "Set whether this profile is the default profile", example = "true")
    private Boolean isDefault;
}
