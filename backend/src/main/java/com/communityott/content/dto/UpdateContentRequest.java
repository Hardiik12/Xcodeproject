package com.communityott.content.dto;

import com.communityott.content.entity.AgeRating;
import com.communityott.content.entity.ContentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request body for updating existing content item metadata")
public class UpdateContentRequest {

    @Size(min = 1, max = 255, message = "Title must be between 1 and 255 characters")
    @Schema(description = "Updated title", example = "Echoes of the Loom: Heritage Edition")
    private String title;

    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    @Schema(description = "Updated description", example = "Extended cultural exploration into Indian handloom traditions.")
    private String description;

    @Schema(description = "Updated content type", example = "DOCUMENTARY")
    private ContentType contentType;

    @Schema(description = "Updated release date", example = "2026-08-20")
    private LocalDate releaseDate;

    @Min(value = 0, message = "Duration in seconds must be greater than or equal to 0")
    @Schema(description = "Updated duration in seconds", example = "3600")
    private Integer durationSeconds;

    @Schema(description = "Updated age rating", example = "U")
    private AgeRating ageRating;

    @Size(max = 500, message = "Thumbnail URL cannot exceed 500 characters")
    @Schema(description = "Updated thumbnail URL", example = "https://cdn.communityott.org/posters/loom_v2.jpg")
    private String thumbnailUrl;

    @Size(max = 500, message = "Banner URL cannot exceed 500 characters")
    @Schema(description = "Updated banner URL", example = "https://cdn.communityott.org/banners/loom_wide_v2.jpg")
    private String bannerUrl;

    @Size(max = 255, message = "Subtitle cannot exceed 255 characters")
    @Schema(description = "Updated subtitle", example = "The Timeless Art of Handloom")
    private String subtitle;

    @Size(max = 500, message = "Short description cannot exceed 500 characters")
    @Schema(description = "Updated short summary", example = "A short exploration into traditional weaving.")
    private String shortDescription;

    @Size(max = 100, message = "Country of origin cannot exceed 100 characters")
    @Schema(description = "Updated country of origin", example = "India")
    private String countryOfOrigin;

    @Schema(description = "Updated ID of the original production language", example = "1")
    private Long originalLanguageId;

    @Size(max = 500, message = "Tags cannot exceed 500 characters")
    @Schema(description = "Updated search and taxonomy tags", example = "handloom,heritage,weaving,textiles")
    private String tags;

    @Schema(description = "Updated list of Category IDs associated with this content", example = "[1, 2]")
    private java.util.List<Long> categoryIds;

    @Schema(description = "Updated list of Language IDs (audio/subtitle) available for this content", example = "[1, 2]")
    private java.util.List<Long> languageIds;

    @Schema(description = "Update featured status", example = "true")
    private Boolean isFeatured;
}
