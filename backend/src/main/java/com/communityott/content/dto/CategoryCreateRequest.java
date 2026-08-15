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
@Schema(description = "Category creation request")
public class CategoryCreateRequest {

    @NotBlank(message = "Category name is required")
    @Size(min = 2, max = 100, message = "Category name must be between 2 and 100 characters")
    @Schema(description = "Category display name", example = "Documentary")
    private String name;

    @Size(max = 100, message = "Slug cannot exceed 100 characters")
    @Schema(description = "URL-friendly slug (auto-generated from name if not supplied)", example = "documentary")
    private String slug;

    @Schema(description = "Description of the category", example = "In-depth real world non-fiction stories")
    private String description;

    @Builder.Default
    @Schema(description = "Whether the category is active and visible", example = "true")
    private Boolean active = true;
}
