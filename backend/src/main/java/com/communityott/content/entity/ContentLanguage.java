package com.communityott.content.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "content_languages")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ContentLanguage {

    @EmbeddedId
    private ContentLanguageId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("contentId")
    @JoinColumn(name = "content_id", nullable = false)
    private Content content;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("languageId")
    @JoinColumn(name = "language_id", nullable = false)
    private Language language;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ContentLanguage(Content content, Language language) {
        this.content = content;
        this.language = language;
        this.id = new ContentLanguageId(
                content != null ? content.getId() : null,
                language != null ? language.getId() : null
        );
        this.createdAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.id == null && this.content != null && this.language != null && this.content.getId() != null && this.language.getId() != null) {
            this.id = new ContentLanguageId(this.content.getId(), this.language.getId());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContentLanguage that = (ContentLanguage) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
