package com.communityott.content.dto;

import com.communityott.content.entity.AgeRating;
import com.communityott.content.entity.ContentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
@Schema(description = "Request body for creating a new content item in the catalog")
public class CreateContentRequest {

    @NotBlank(message = "Title is required")
    @Size(min = 1, max = 255, message = "Title must be between 1 and 255 characters")
    @Schema(description = "Content title", example = "Echoes of the Loom")
    private String title;

    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    @Schema(description = "Content storyline or synopsis", example = "A cultural exploration into the timeless art of handloom weaving.")
    private String description;

    @NotNull(message = "Content type is required")
    @Schema(description = "Type of content (MOVIE, DOCUMENTARY, SERIES, EPISODE)", example = "DOCUMENTARY")
    private ContentType contentType;

    @Schema(description = "Release date of the content", example = "2026-08-15")
    private LocalDate releaseDate;

    @Min(value = 0, message = "Duration in seconds must be greater than or equal to 0")
    @Schema(description = "Runtime duration in seconds", example = "3240")
    private Integer durationSeconds;

    @Schema(description = "Age rating classification", example = "U")
    private AgeRating ageRating;

    @Size(max = 500, message = "Thumbnail URL cannot exceed 500 characters")
    @Schema(description = "Poster/Thumbnail image URL", example = "https://cdn.communityott.org/posters/loom.jpg")
    private String thumbnailUrl;

    @Size(max = 500, message = "Banner URL cannot exceed 500 characters")
    @Schema(description = "Hero banner image URL", example = "https://cdn.communityott.org/banners/loom_wide.jpg")
    private String bannerUrl;

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

    @Schema(description = "List of Category IDs associated with this content", example = "[1, 2]")
    private java.util.List<Long> categoryIds;

    @Schema(description = "List of Language IDs (audio/subtitle) available for this content", example = "[1, 2]")
    private java.util.List<Long> languageIds;

    @Schema(description = "Whether to mark this content as featured", example = "false")
    private Boolean isFeatured;
}
