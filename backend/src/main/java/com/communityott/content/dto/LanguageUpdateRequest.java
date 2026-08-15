package com.communityott.content.dto;

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
@Schema(description = "Language update request")
public class LanguageUpdateRequest {

    @Size(min = 2, max = 100, message = "Language name must be between 2 and 100 characters")
    @Schema(description = "Updated language display name", example = "Telugu (Classical)")
    private String name;

    @Size(min = 2, max = 20, message = "Language code must be between 2 and 20 characters")
    @Schema(description = "Updated ISO language code", example = "te")
    private String code;

    @Schema(description = "Language active status", example = "true")
    private Boolean active;
}
