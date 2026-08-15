package com.communityott.content.service;

import com.communityott.common.exception.*;
import com.communityott.content.dto.*;
import com.communityott.content.entity.*;
import com.communityott.content.repository.*;
import com.communityott.content.specification.ContentSpecification;
import com.communityott.content.util.SortValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentService {

    private final ContentRepository contentRepository;
    private final CategoryRepository categoryRepository;
    private final LanguageRepository languageRepository;
    private final ContentCategoryRepository contentCategoryRepository;
    private final ContentLanguageRepository contentLanguageRepository;

    // ==========================================
    // 1. PUBLIC / CONSUMER CATALOG (PUBLISHED ONLY)
    // ==========================================

    @Transactional(readOnly = true)
    public Page<ContentSummaryResponse> getPublishedCatalog(ContentFilterCriteria criteria, Pageable pageable) {
        Pageable sanitizedPageable = SortValidator.validateAndSanitizePageable(pageable);

        // Enforce strictly PUBLISHED content for public catalog
        if (criteria == null) {
            criteria = new ContentFilterCriteria();
        }
        criteria.setStatus(ContentStatus.PUBLISHED);

        Specification<Content> spec = ContentSpecification.withCriteria(criteria);
        Page<Content> contentPage = contentRepository.findAll(spec, sanitizedPageable);

        return contentPage.map(ContentSummaryResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public ContentResponse getPublishedContentById(Long contentId) {
        log.debug("Fetching published content details for ID: {}", contentId);

        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new ContentNotFoundException(contentId));

        if (content.getStatus() != ContentStatus.PUBLISHED) {
            log.warn("Attempted to access non-published content ID: {} with status: {}", contentId, content.getStatus());
            throw new ContentNotPublishedException(contentId);
        }

        return ContentResponse.fromEntity(content);
    }

    @Transactional(readOnly = true)
    public List<ContentSummaryResponse> getFeaturedContent() {
        log.debug("Fetching featured published content");
        return contentRepository.findByStatusAndIsFeaturedTrueOrderByCreatedAtDesc(ContentStatus.PUBLISHED).stream()
                .map(ContentSummaryResponse::fromEntity)
                .toList();
    }

    // ==========================================
    // 2. ADMIN / CONTENT MANAGEMENT
    // ==========================================

    @Transactional
    public ContentResponse createContent(CreateContentRequest request, Long adminUserId) {
        log.info("Admin user ID: {} creating content '{}'", adminUserId, request.getTitle());

        Language originalLanguage = null;
        if (request.getOriginalLanguageId() != null) {
            originalLanguage = languageRepository.findById(request.getOriginalLanguageId())
                    .orElseThrow(() -> new LanguageNotFoundException(request.getOriginalLanguageId()));
        }

        Content content = Content.builder()
                .title(request.getTitle().trim())
                .subtitle(request.getSubtitle())
                .description(request.getDescription())
                .shortDescription(request.getShortDescription())
                .countryOfOrigin(request.getCountryOfOrigin())
                .originalLanguage(originalLanguage)
                .tags(request.getTags())
                .contentType(request.getContentType())
                .releaseDate(request.getReleaseDate())
                .durationSeconds(request.getDurationSeconds())
                .ageRating(request.getAgeRating() != null ? request.getAgeRating() : AgeRating.U)
                .status(ContentStatus.DRAFT)
                .thumbnailUrl(request.getThumbnailUrl())
                .bannerUrl(request.getBannerUrl())
                .isFeatured(Boolean.TRUE.equals(request.getIsFeatured()))
                .createdBy(adminUserId)
                .updatedBy(adminUserId)
                .build();

        Content savedContent = contentRepository.save(content);

        // Attach categories
        if (request.getCategoryIds() != null && !request.getCategoryIds().isEmpty()) {
            Set<Long> uniqueCatIds = new HashSet<>(request.getCategoryIds());
            for (Long catId : uniqueCatIds) {
                Category cat = categoryRepository.findById(catId)
                        .orElseThrow(() -> new CategoryNotFoundException(catId));
                ContentCategory cc = new ContentCategory(savedContent, cat);
                savedContent.getContentCategories().add(cc);
            }
        }

        // Attach languages
        if (request.getLanguageIds() != null && !request.getLanguageIds().isEmpty()) {
            Set<Long> uniqueLangIds = new HashSet<>(request.getLanguageIds());
            for (Long langId : uniqueLangIds) {
                Language lang = languageRepository.findById(langId)
                        .orElseThrow(() -> new LanguageNotFoundException(langId));
                ContentLanguage cl = new ContentLanguage(savedContent, lang);
                savedContent.getContentLanguages().add(cl);
            }
        }

        Content finalizedContent = contentRepository.save(savedContent);
        log.info("Successfully created content ID: {} with status: DRAFT", finalizedContent.getId());
        return ContentResponse.fromEntity(finalizedContent);
    }

    @Transactional
    public ContentResponse updateContent(Long contentId, UpdateContentRequest request, Long adminUserId) {
        log.info("Admin user ID: {} updating content ID: {}", adminUserId, contentId);

        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new ContentNotFoundException(contentId));

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            content.setTitle(request.getTitle().trim());
        }

        if (request.getSubtitle() != null) {
            content.setSubtitle(request.getSubtitle().isBlank() ? null : request.getSubtitle().trim());
        }

        if (request.getDescription() != null) {
            content.setDescription(request.getDescription().trim());
        }

        if (request.getShortDescription() != null) {
            content.setShortDescription(request.getShortDescription().isBlank() ? null : request.getShortDescription().trim());
        }

        if (request.getCountryOfOrigin() != null) {
            content.setCountryOfOrigin(request.getCountryOfOrigin().isBlank() ? null : request.getCountryOfOrigin().trim());
        }

        if (request.getOriginalLanguageId() != null) {
            Language origLang = languageRepository.findById(request.getOriginalLanguageId())
                    .orElseThrow(() -> new LanguageNotFoundException(request.getOriginalLanguageId()));
            content.setOriginalLanguage(origLang);
        }

        if (request.getTags() != null) {
            content.setTags(request.getTags().isBlank() ? null : request.getTags().trim());
        }

        if (request.getContentType() != null) {
            content.setContentType(request.getContentType());
        }

        if (request.getReleaseDate() != null) {
            content.setReleaseDate(request.getReleaseDate());
        }

        if (request.getDurationSeconds() != null) {
            content.setDurationSeconds(request.getDurationSeconds());
        }

        if (request.getAgeRating() != null) {
            content.setAgeRating(request.getAgeRating());
        }

        if (request.getThumbnailUrl() != null) {
            content.setThumbnailUrl(request.getThumbnailUrl().isBlank() ? null : request.getThumbnailUrl().trim());
        }

        if (request.getBannerUrl() != null) {
            content.setBannerUrl(request.getBannerUrl().isBlank() ? null : request.getBannerUrl().trim());
        }

        if (request.getIsFeatured() != null) {
            content.setFeatured(request.getIsFeatured());
        }

        // Update category associations if provided
        if (request.getCategoryIds() != null) {
            content.getContentCategories().clear();
            Set<Long> uniqueCatIds = new HashSet<>(request.getCategoryIds());
            for (Long catId : uniqueCatIds) {
                Category cat = categoryRepository.findById(catId)
                        .orElseThrow(() -> new CategoryNotFoundException(catId));
                ContentCategory cc = new ContentCategory(content, cat);
                content.getContentCategories().add(cc);
            }
        }

        // Update language associations if provided
        if (request.getLanguageIds() != null) {
            content.getContentLanguages().clear();
            Set<Long> uniqueLangIds = new HashSet<>(request.getLanguageIds());
            for (Long langId : uniqueLangIds) {
                Language lang = languageRepository.findById(langId)
                        .orElseThrow(() -> new LanguageNotFoundException(langId));
                ContentLanguage cl = new ContentLanguage(content, lang);
                content.getContentLanguages().add(cl);
            }
        }

        content.setUpdatedBy(adminUserId);
        Content updatedContent = contentRepository.save(content);
        log.info("Successfully updated content ID: {}", updatedContent.getId());

        return ContentResponse.fromEntity(updatedContent);
    }

    @Transactional
    public ContentResponse updateMetadata(Long contentId, ContentMetadataUpdateRequest request, Long adminUserId) {
        log.info("Admin user ID: {} updating metadata for content ID: {}", adminUserId, contentId);

        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new ContentNotFoundException(contentId));

        if (request.getSubtitle() != null) {
            content.setSubtitle(request.getSubtitle().isBlank() ? null : request.getSubtitle().trim());
        }

        if (request.getShortDescription() != null) {
            content.setShortDescription(request.getShortDescription().isBlank() ? null : request.getShortDescription().trim());
        }

        if (request.getCountryOfOrigin() != null) {
            content.setCountryOfOrigin(request.getCountryOfOrigin().isBlank() ? null : request.getCountryOfOrigin().trim());
        }

        if (request.getOriginalLanguageId() != null) {
            Language origLang = languageRepository.findById(request.getOriginalLanguageId())
                    .orElseThrow(() -> new LanguageNotFoundException(request.getOriginalLanguageId()));
            content.setOriginalLanguage(origLang);
        }

        if (request.getTags() != null) {
            content.setTags(request.getTags().isBlank() ? null : request.getTags().trim());
        }

        if (request.getCategoryIds() != null) {
            content.getContentCategories().clear();
            Set<Long> uniqueCatIds = new HashSet<>(request.getCategoryIds());
            for (Long catId : uniqueCatIds) {
                Category cat = categoryRepository.findById(catId)
                        .orElseThrow(() -> new CategoryNotFoundException(catId));
                ContentCategory cc = new ContentCategory(content, cat);
                content.getContentCategories().add(cc);
            }
        }

        if (request.getLanguageIds() != null) {
            content.getContentLanguages().clear();
            Set<Long> uniqueLangIds = new HashSet<>(request.getLanguageIds());
            for (Long langId : uniqueLangIds) {
                Language lang = languageRepository.findById(langId)
                        .orElseThrow(() -> new LanguageNotFoundException(langId));
                ContentLanguage cl = new ContentLanguage(content, lang);
                content.getContentLanguages().add(cl);
            }
        }

        content.setUpdatedBy(adminUserId);
        Content saved = contentRepository.save(content);
        return ContentResponse.fromEntity(saved);
    }

    @Transactional
    public ContentResponse assignCategory(Long contentId, Long categoryId, Long adminUserId) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new ContentNotFoundException(contentId));
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));

        boolean exists = content.getContentCategories().stream()
                .anyMatch(cc -> cc.getCategory().getId().equals(categoryId));

        if (!exists) {
            ContentCategory cc = new ContentCategory(content, category);
            content.getContentCategories().add(cc);
            content.setUpdatedBy(adminUserId);
            content = contentRepository.save(content);
        }
        return ContentResponse.fromEntity(content);
    }

    @Transactional
    public ContentResponse removeCategory(Long contentId, Long categoryId, Long adminUserId) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new ContentNotFoundException(contentId));

        content.getContentCategories().removeIf(cc -> cc.getCategory().getId().equals(categoryId));
        content.setUpdatedBy(adminUserId);
        content = contentRepository.save(content);
        return ContentResponse.fromEntity(content);
    }

    @Transactional
    public ContentResponse assignLanguage(Long contentId, Long languageId, Long adminUserId) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new ContentNotFoundException(contentId));
        Language language = languageRepository.findById(languageId)
                .orElseThrow(() -> new LanguageNotFoundException(languageId));

        boolean exists = content.getContentLanguages().stream()
                .anyMatch(cl -> cl.getLanguage().getId().equals(languageId));

        if (!exists) {
            ContentLanguage cl = new ContentLanguage(content, language);
            content.getContentLanguages().add(cl);
            content.setUpdatedBy(adminUserId);
            content = contentRepository.save(content);
        }
        return ContentResponse.fromEntity(content);
    }

    @Transactional
    public ContentResponse removeLanguage(Long contentId, Long languageId, Long adminUserId) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new ContentNotFoundException(contentId));

        content.getContentLanguages().removeIf(cl -> cl.getLanguage().getId().equals(languageId));
        content.setUpdatedBy(adminUserId);
        content = contentRepository.save(content);
        return ContentResponse.fromEntity(content);
    }

    private final ContentLifecycleService contentLifecycleService;

    @Transactional(readOnly = true)
    public ContentStatusSummaryResponse getContentStatusSummary() {
        long draft = contentRepository.countByStatus(ContentStatus.DRAFT);
        long uploading = contentRepository.countByStatus(ContentStatus.UPLOADING);
        long processing = contentRepository.countByStatus(ContentStatus.PROCESSING);
        long ready = contentRepository.countByStatus(ContentStatus.READY);
        long published = contentRepository.countByStatus(ContentStatus.PUBLISHED);
        long unpublished = contentRepository.countByStatus(ContentStatus.UNPUBLISHED);
        long failed = contentRepository.countByStatus(ContentStatus.FAILED);
        long archived = contentRepository.countByStatus(ContentStatus.ARCHIVED);
        long total = draft + uploading + processing + ready + published + unpublished + failed + archived;

        return ContentStatusSummaryResponse.builder()
                .draft(draft)
                .uploading(uploading)
                .processing(processing)
                .ready(ready)
                .published(published)
                .unpublished(unpublished)
                .failed(failed)
                .archived(archived)
                .total(total)
                .build();
    }

    @Transactional
    public ContentResponse transitionStatus(Long contentId, ContentStatus targetStatus, Long adminUserId) {
        return contentLifecycleService.transitionStatus(contentId, targetStatus, adminUserId);
    }

    @Transactional
    public ContentResponse publishContent(Long contentId, Long adminUserId) {
        return contentLifecycleService.publish(contentId, adminUserId);
    }

    @Transactional
    public ContentResponse unpublishContent(Long contentId, Long adminUserId) {
        return contentLifecycleService.unpublish(contentId, adminUserId);
    }

    @Transactional
    public ContentResponse archiveContent(Long contentId, Long adminUserId) {
        return contentLifecycleService.archive(contentId, adminUserId);
    }

    @Transactional
    public ContentResponse retryProcessing(Long contentId, Long adminUserId) {
        return contentLifecycleService.retryProcessing(contentId, adminUserId);
    }

    @Transactional(readOnly = true)
    public ContentResponse getContentForAdmin(Long contentId) {
        log.debug("Admin fetching content ID: {}", contentId);
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new ContentNotFoundException(contentId));
        return ContentResponse.fromEntity(content);
    }

    @Transactional(readOnly = true)
    public Page<ContentResponse> listContentForAdmin(ContentFilterCriteria criteria, Pageable pageable) {
        Pageable sanitizedPageable = SortValidator.validateAndSanitizePageable(pageable);
        if (criteria == null) {
            criteria = new ContentFilterCriteria();
        }
        Specification<Content> spec = ContentSpecification.withCriteria(criteria);
        Page<Content> page = contentRepository.findAll(spec, sanitizedPageable);
        return page.map(ContentResponse::fromEntity);
    }
}
