package com.communityott.content.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "content")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Content {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "subtitle", length = 255)
    private String subtitle;

    @Column(name = "short_description", length = 500)
    private String shortDescription;

    @Column(name = "country_of_origin", length = 100)
    private String countryOfOrigin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_language_id")
    private Language originalLanguage;

    @Column(name = "tags", length = 500)
    private String tags;

    @OneToMany(mappedBy = "content", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private java.util.Set<ContentCategory> contentCategories = new java.util.HashSet<>();

    @OneToMany(mappedBy = "content", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private java.util.Set<ContentLanguage> contentLanguages = new java.util.HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 50)
    private ContentType contentType;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(name = "age_rating", nullable = false, length = 20)
    @Builder.Default
    private AgeRating ageRating = AgeRating.U;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private ContentStatus status = ContentStatus.DRAFT;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "banner_url", length = 500)
    private String bannerUrl;

    @Column(name = "is_featured", nullable = false)
    @Builder.Default
    private boolean isFeatured = false;

    @jakarta.persistence.OneToMany(mappedBy = "content", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private java.util.List<VideoAsset> videoAssets = new java.util.ArrayList<>();

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.ageRating == null) {
            this.ageRating = AgeRating.U;
        }
        if (this.status == null) {
            this.status = ContentStatus.DRAFT;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Content content = (Content) o;
        return id != null && Objects.equals(id, content.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
