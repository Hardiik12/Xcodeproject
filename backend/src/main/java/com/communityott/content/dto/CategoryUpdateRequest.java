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
@Schema(description = "Category update request")
public class CategoryUpdateRequest {

    @Size(min = 2, max = 100, message = "Category name must be between 2 and 100 characters")
    @Schema(description = "Updated category display name", example = "Historical Chronicles")
    private String name;

    @Size(max = 100, message = "Slug cannot exceed 100 characters")
    @Schema(description = "Updated URL-friendly slug", example = "historical-chronicles")
    private String slug;

    @Schema(description = "Updated description of the category", example = "Comprehensive archives of world and regional history")
    private String description;

    @Schema(description = "Category active status", example = "true")
    private Boolean active;
}
