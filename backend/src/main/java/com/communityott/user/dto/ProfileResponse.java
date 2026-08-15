package com.communityott.user.dto;

import com.communityott.user.entity.Profile;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response representation of an OTT viewing profile")
public class ProfileResponse {

    @Schema(description = "Profile unique ID", example = "1")
    private Long id;

    @Schema(description = "Associated user ID", example = "10")
    private Long userId;

    @Schema(description = "Viewing profile display name", example = "Hardik")
    private String displayName;

    @Schema(description = "Profile avatar image URL", example = "https://cdn.communityott.org/avatars/1.png")
    private String avatarUrl;

    @Schema(description = "Profile preferred language code", example = "te")
    private String preferredLanguage;

    @Schema(description = "Whether this is the user's primary/default profile", example = "true")
    private boolean isDefault;

    @Schema(description = "Profile creation timestamp")
    private Instant createdAt;

    @Schema(description = "Profile last update timestamp")
    private Instant updatedAt;

    public static ProfileResponse fromEntity(Profile profile) {
        return ProfileResponse.builder()
                .id(profile.getId())
                .userId(profile.getUser().getId())
                .displayName(profile.getDisplayName())
                .avatarUrl(profile.getAvatarUrl())
                .preferredLanguage(profile.getPreferredLanguage())
                .isDefault(profile.isDefault())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }
}
