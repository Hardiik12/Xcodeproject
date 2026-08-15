package com.communityott.content.dto;

import com.communityott.content.entity.Language;
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
@Schema(description = "Language response DTO")
public class LanguageResponse {

    @Schema(description = "Language ID", example = "1")
    private Long id;

    @Schema(description = "Language display name", example = "Telugu")
    private String name;

    @Schema(description = "Language code", example = "te")
    private String code;

    @Schema(description = "Active status", example = "true")
    private boolean active;

    @Schema(description = "Creation timestamp")
    private Instant createdAt;

    @Schema(description = "Last update timestamp")
    private Instant updatedAt;

    public static LanguageResponse fromEntity(Language language) {
        if (language == null) {
            return null;
        }
        return LanguageResponse.builder()
                .id(language.getId())
                .name(language.getName())
                .code(language.getCode())
                .active(language.isActive())
                .createdAt(language.getCreatedAt())
                .updatedAt(language.getUpdatedAt())
                .build();
    }
}
