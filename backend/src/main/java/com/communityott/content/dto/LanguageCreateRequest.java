package com.communityott.content.dto;

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
@Schema(description = "Language creation request")
public class LanguageCreateRequest {

    @NotBlank(message = "Language name is required")
    @Size(min = 2, max = 100, message = "Language name must be between 2 and 100 characters")
    @Schema(description = "Language display name", example = "Telugu")
    private String name;

    @NotBlank(message = "Language code is required")
    @Size(min = 2, max = 20, message = "Language code must be between 2 and 20 characters")
    @Schema(description = "ISO language code or unique identifier", example = "te")
    private String code;

    @Builder.Default
    @Schema(description = "Whether the language is active", example = "true")
    private Boolean active = true;
}
