package com.communityott.content.dto;

import com.communityott.content.entity.AgeRating;
import com.communityott.content.entity.Content;
import com.communityott.content.entity.ContentType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Compact content summary for catalog feeds and discovery rails")
public class ContentSummaryResponse {

    @Schema(description = "Content unique ID", example = "101")
    private Long id;

    @Schema(description = "Content title", example = "Echoes of the Loom")
    private String title;

    @Schema(description = "Type of content", example = "DOCUMENTARY")
    private ContentType contentType;

    @Schema(description = "Release date", example = "2026-08-15")
    private LocalDate releaseDate;

    @Schema(description = "Runtime duration in seconds", example = "3240")
    private Integer durationSeconds;

    @Schema(description = "Age classification rating", example = "U")
    private AgeRating ageRating;

    @Schema(description = "Poster thumbnail URL", example = "https://cdn.communityott.org/posters/loom.jpg")
    private String thumbnailUrl;

    @Schema(description = "Hero banner URL", example = "https://cdn.communityott.org/banners/loom_wide.jpg")
    private String bannerUrl;

    @Schema(description = "Optional subtitle", example = "The Timeless Art of Handloom")
    private String subtitle;

    @Schema(description = "Short preview description", example = "A short exploration into traditional weaving.")
    private String shortDescription;

    @Schema(description = "Original language code", example = "te")
    private String originalLanguageCode;

    @Schema(description = "Whether the item is featured", example = "true")
    private boolean isFeatured;

    @Schema(description = "List of category slugs associated with this content", example = "[\"documentary\", \"culture\"]")
    @Builder.Default
    private java.util.List<String> categorySlugs = new java.util.ArrayList<>();

    public static ContentSummaryResponse fromEntity(Content content) {
        if (content == null) {
            return null;
        }
        java.util.List<String> slugs = content.getContentCategories() != null
                ? content.getContentCategories().stream()
                    .map(cc -> cc.getCategory() != null ? cc.getCategory().getSlug() : null)
                    .filter(java.util.Objects::nonNull)
                    .toList()
                : java.util.Collections.emptyList();

        String langCode = content.getOriginalLanguage() != null ? content.getOriginalLanguage().getCode() : null;

        return ContentSummaryResponse.builder()
                .id(content.getId())
                .title(content.getTitle())
                .subtitle(content.getSubtitle())
                .shortDescription(content.getShortDescription())
                .originalLanguageCode(langCode)
                .categorySlugs(slugs)
                .contentType(content.getContentType())
                .releaseDate(content.getReleaseDate())
                .durationSeconds(content.getDurationSeconds())
                .ageRating(content.getAgeRating())
                .thumbnailUrl(content.getThumbnailUrl())
                .bannerUrl(content.getBannerUrl())
                .isFeatured(content.isFeatured())
                .build();
    }
}
