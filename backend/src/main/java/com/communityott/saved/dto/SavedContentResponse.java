package com.communityott.saved.dto;

import com.communityott.content.entity.Content;
import com.communityott.content.entity.ContentStatus;
import com.communityott.content.entity.ContentType;
import com.communityott.saved.entity.SavedContent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavedContentResponse {

    private Long id;
    private Long contentId;
    private String title;
    private String subtitle;
    private String thumbnailUrl;
    private String bannerUrl;
    private ContentType contentType;
    private Integer durationSeconds;
    private Instant savedAt;
    private Boolean isPlayable;
    private String availability;

    public static SavedContentResponse from(SavedContent savedContent) {
        if (savedContent == null) {
            return null;
        }

        Content content = savedContent.getContent();
        boolean playable = content != null && content.getStatus() == ContentStatus.PUBLISHED;
        String availabilityStr = playable ? "AVAILABLE" : "UNAVAILABLE";

        return SavedContentResponse.builder()
                .id(savedContent.getId())
                .contentId(content != null ? content.getId() : null)
                .title(content != null ? content.getTitle() : "Unknown Title")
                .subtitle(content != null ? content.getSubtitle() : null)
                .thumbnailUrl(content != null ? content.getThumbnailUrl() : null)
                .bannerUrl(content != null ? content.getBannerUrl() : null)
                .contentType(content != null ? content.getContentType() : null)
                .durationSeconds(content != null ? content.getDurationSeconds() : 0)
                .savedAt(savedContent.getSavedAt())
                .isPlayable(playable)
                .availability(availabilityStr)
                .build();
    }
}
