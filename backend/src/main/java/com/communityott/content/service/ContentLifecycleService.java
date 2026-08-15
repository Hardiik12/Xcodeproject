package com.communityott.content.service;

import com.communityott.common.exception.ContentNotFoundException;
import com.communityott.common.exception.ContentNotPublishableException;
import com.communityott.common.exception.InvalidContentStateTransitionException;
import com.communityott.content.dto.ContentResponse;
import com.communityott.content.dto.PublishabilityResult;
import com.communityott.content.entity.Content;
import com.communityott.content.entity.ContentStatus;
import com.communityott.content.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentLifecycleService {

    private final ContentRepository contentRepository;
    private final ContentPublishabilityChecker publishabilityChecker;

    // Allowed transition state machine
    private static final Map<ContentStatus, Set<ContentStatus>> ALLOWED_TRANSITIONS = Map.of(
            ContentStatus.DRAFT, EnumSet.of(ContentStatus.UPLOADING),
            ContentStatus.UPLOADING, EnumSet.of(ContentStatus.PROCESSING, ContentStatus.FAILED),
            ContentStatus.PROCESSING, EnumSet.of(ContentStatus.READY, ContentStatus.FAILED),
            ContentStatus.READY, EnumSet.of(ContentStatus.PUBLISHED, ContentStatus.ARCHIVED, ContentStatus.UPLOADING),
            ContentStatus.FAILED, EnumSet.of(ContentStatus.PROCESSING, ContentStatus.UPLOADING, ContentStatus.ARCHIVED),
            ContentStatus.PUBLISHED, EnumSet.of(ContentStatus.UNPUBLISHED, ContentStatus.ARCHIVED),
            ContentStatus.UNPUBLISHED, EnumSet.of(ContentStatus.PUBLISHED, ContentStatus.ARCHIVED),
            ContentStatus.ARCHIVED, EnumSet.noneOf(ContentStatus.class)
    );

    @Transactional
    public ContentResponse transitionStatus(Long contentId, ContentStatus targetStatus, Long actorUserId) {
        log.info("Actor ID: {} requesting status transition for content ID: {} to {}", actorUserId, contentId, targetStatus);

        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new ContentNotFoundException(contentId));

        if (targetStatus == ContentStatus.PUBLISHED) {
            PublishabilityResult result = publishabilityChecker.checkPublishability(content);
            if (!result.isPublishable()) {
                throw new ContentNotPublishableException(result.missingPrerequisites());
            }
        }

        validateAndApplyTransition(content, targetStatus, actorUserId);
        Content saved = contentRepository.save(content);
        log.info("Content ID: {} transitioned to {}", contentId, targetStatus);
        return ContentResponse.fromEntity(saved);
    }

    @Transactional
    public ContentResponse publish(Long contentId, Long actorUserId) {
        log.info("Actor ID: {} publishing content ID: {}", actorUserId, contentId);

        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new ContentNotFoundException(contentId));

        if (content.getStatus() == ContentStatus.PUBLISHED) {
            return ContentResponse.fromEntity(content);
        }

        PublishabilityResult result = publishabilityChecker.checkPublishability(content);
        if (!result.isPublishable()) {
            throw new ContentNotPublishableException(result.missingPrerequisites());
        }

        validateAndApplyTransition(content, ContentStatus.PUBLISHED, actorUserId);
        Content saved = contentRepository.save(content);
        return ContentResponse.fromEntity(saved);
    }

    @Transactional
    public ContentResponse unpublish(Long contentId, Long actorUserId) {
        log.info("Actor ID: {} unpublishing content ID: {}", actorUserId, contentId);

        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new ContentNotFoundException(contentId));

        if (content.getStatus() == ContentStatus.UNPUBLISHED) {
            return ContentResponse.fromEntity(content);
        }

        validateAndApplyTransition(content, ContentStatus.UNPUBLISHED, actorUserId);
        Content saved = contentRepository.save(content);
        return ContentResponse.fromEntity(saved);
    }

    @Transactional
    public ContentResponse archive(Long contentId, Long actorUserId) {
        log.info("Actor ID: {} archiving content ID: {}", actorUserId, contentId);

        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new ContentNotFoundException(contentId));

        if (content.getStatus() == ContentStatus.ARCHIVED) {
            return ContentResponse.fromEntity(content);
        }

        validateAndApplyTransition(content, ContentStatus.ARCHIVED, actorUserId);
        Content saved = contentRepository.save(content);
        return ContentResponse.fromEntity(saved);
    }

    @Transactional
    public ContentResponse retryProcessing(Long contentId, Long actorUserId) {
        log.info("Actor ID: {} retrying processing for content ID: {}", actorUserId, contentId);

        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new ContentNotFoundException(contentId));

        if (content.getStatus() != ContentStatus.FAILED) {
            throw new InvalidContentStateTransitionException("Only FAILED content can have processing retried (current status: " + content.getStatus() + ")");
        }

        validateAndApplyTransition(content, ContentStatus.PROCESSING, actorUserId);
        Content saved = contentRepository.save(content);
        return ContentResponse.fromEntity(saved);
    }

    private void validateAndApplyTransition(Content content, ContentStatus targetStatus, Long actorUserId) {
        ContentStatus currentStatus = content.getStatus();

        if (currentStatus == targetStatus) {
            return;
        }

        Set<ContentStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, EnumSet.noneOf(ContentStatus.class));
        if (!allowed.contains(targetStatus)) {
            throw new InvalidContentStateTransitionException(currentStatus, targetStatus);
        }

        content.setStatus(targetStatus);
        content.setUpdatedBy(actorUserId);
    }
}
