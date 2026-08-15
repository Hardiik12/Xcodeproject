package com.communityott.content.dto;

import com.communityott.content.entity.AgeRating;
import com.communityott.content.entity.Content;
import com.communityott.content.entity.ContentStatus;
import com.communityott.content.entity.ContentType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Detailed content item response")
public class ContentResponse {

    @Schema(description = "Content unique ID", example = "101")
    private Long id;

    @Schema(description = "Content title", example = "Echoes of the Loom")
    private String title;

    @Schema(description = "Content description", example = "A cultural exploration into the timeless art of handloom weaving.")
    private String description;

    @Schema(description = "Type of content", example = "DOCUMENTARY")
    private ContentType contentType;

    @Schema(description = "Official release date", example = "2026-08-15")
    private LocalDate releaseDate;

    @Schema(description = "Runtime duration in seconds", example = "3240")
    private Integer durationSeconds;

    @Schema(description = "Age classification rating", example = "U")
    private AgeRating ageRating;

    @Schema(description = "Lifecycle publication status", example = "PUBLISHED")
    private ContentStatus status;

    @Schema(description = "Poster thumbnail URL", example = "https://cdn.communityott.org/posters/loom.jpg")
    private String thumbnailUrl;

    @Schema(description = "Hero banner URL", example = "https://cdn.communityott.org/banners/loom_wide.jpg")
    private String bannerUrl;

    @Schema(description = "Whether the item is featured in hero rails", example = "true")
    private boolean isFeatured;

    @Schema(description = "Optional subtitle", example = "The Timeless Art of Handloom")
    private String subtitle;

    @Schema(description = "Compact preview summary", example = "A short exploration into traditional weaving.")
    private String shortDescription;

    @Schema(description = "Country of origin", example = "India")
    private String countryOfOrigin;

    @Schema(description = "Original production language")
    private LanguageResponse originalLanguage;

    @Schema(description = "Taxonomy and search tags", example = "handloom,heritage,weaving")
    private String tags;

    @Schema(description = "Associated content categories")
    @Builder.Default
    private java.util.List<CategoryResponse> categories = new java.util.ArrayList<>();

    @Schema(description = "Available audio/subtitle languages")
    @Builder.Default
    private java.util.List<LanguageResponse> languages = new java.util.ArrayList<>();

    @Schema(description = "Optimistic locking version", example = "0")
    private Long version;

    @Schema(description = "User ID who created the content", example = "1")
    private Long createdBy;

    @Schema(description = "User ID who last updated the content", example = "1")
    private Long updatedBy;

    @Schema(description = "Content creation timestamp")
    private Instant createdAt;

    @Schema(description = "Content last update timestamp")
    private Instant updatedAt;

    public static ContentResponse fromEntity(Content content) {
        if (content == null) {
            return null;
        }
        java.util.List<CategoryResponse> categoryResponses = content.getContentCategories() != null
                ? content.getContentCategories().stream()
                    .map(cc -> CategoryResponse.fromEntity(cc.getCategory()))
                    .filter(java.util.Objects::nonNull)
                    .toList()
                : java.util.Collections.emptyList();

        java.util.List<LanguageResponse> languageResponses = content.getContentLanguages() != null
                ? content.getContentLanguages().stream()
                    .map(cl -> LanguageResponse.fromEntity(cl.getLanguage()))
                    .filter(java.util.Objects::nonNull)
                    .toList()
                : java.util.Collections.emptyList();

        return ContentResponse.builder()
                .id(content.getId())
                .title(content.getTitle())
                .subtitle(content.getSubtitle())
                .description(content.getDescription())
                .shortDescription(content.getShortDescription())
                .countryOfOrigin(content.getCountryOfOrigin())
                .originalLanguage(LanguageResponse.fromEntity(content.getOriginalLanguage()))
                .tags(content.getTags())
                .categories(categoryResponses)
                .languages(languageResponses)
                .contentType(content.getContentType())
                .releaseDate(content.getReleaseDate())
                .durationSeconds(content.getDurationSeconds())
                .ageRating(content.getAgeRating())
                .status(content.getStatus())
                .thumbnailUrl(content.getThumbnailUrl())
                .bannerUrl(content.getBannerUrl())
                .isFeatured(content.isFeatured())
                .version(content.getVersion())
                .createdBy(content.getCreatedBy())
                .updatedBy(content.getUpdatedBy())
                .createdAt(content.getCreatedAt())
                .updatedAt(content.getUpdatedAt())
                .build();
    }
}
