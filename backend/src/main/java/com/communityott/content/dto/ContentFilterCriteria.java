package com.communityott.content.dto;

import com.communityott.content.entity.AgeRating;
import com.communityott.content.entity.ContentStatus;
import com.communityott.content.entity.ContentType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Criteria parameters for dynamic catalog filtering")
public class ContentFilterCriteria {

    @Schema(description = "Mandatory or optional publication status", example = "PUBLISHED")
    private ContentStatus status;

    @Schema(description = "Filter by content type", example = "DOCUMENTARY")
    private ContentType contentType;

    @Schema(description = "Filter by category slug or ID", example = "documentary")
    private String category;

    @Schema(description = "Filter by language code or ID", example = "te")
    private String language;

    @Schema(description = "Filter by age rating classification", example = "U")
    private AgeRating ageRating;

    @Schema(description = "Search term matched across title, subtitle, description, and tags", example = "weaving")
    private String search;
}
