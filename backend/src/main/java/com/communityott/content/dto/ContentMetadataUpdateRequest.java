package com.communityott.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to update taxonomy, metadata, categories, and languages of a content item")
public class ContentMetadataUpdateRequest {

    @Size(max = 255, message = "Subtitle cannot exceed 255 characters")
    @Schema(description = "Optional subtitle", example = "The Timeless Art of Handloom")
    private String subtitle;

    @Size(max = 500, message = "Short description cannot exceed 500 characters")
    @Schema(description = "Compact preview summary", example = "A short exploration into traditional weaving.")
    private String shortDescription;

    @Size(max = 100, message = "Country of origin cannot exceed 100 characters")
    @Schema(description = "Country of origin", example = "India")
    private String countryOfOrigin;

    @Schema(description = "ID of the original production language", example = "1")
    private Long originalLanguageId;

    @Size(max = 500, message = "Tags cannot exceed 500 characters")
    @Schema(description = "Search and taxonomy tags", example = "handloom,heritage,weaving,textiles")
    private String tags;

    @Schema(description = "List of Category IDs associated with this content", example = "[1, 2, 3]")
    private List<Long> categoryIds;

    @Schema(description = "List of Language IDs (audio/subtitle) available for this content", example = "[1, 2]")
    private List<Long> languageIds;
}
