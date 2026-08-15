package com.communityott.content.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContentLanguageId implements Serializable {

    @Column(name = "content_id")
    private Long contentId;

    @Column(name = "language_id")
    private Long languageId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContentLanguageId that = (ContentLanguageId) o;
        return Objects.equals(contentId, that.contentId) && Objects.equals(languageId, that.languageId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contentId, languageId);
    }
}
