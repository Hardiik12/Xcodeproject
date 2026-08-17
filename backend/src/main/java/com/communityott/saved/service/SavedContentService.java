package com.communityott.saved.service;

import com.communityott.common.exception.ContentNotFoundException;
import com.communityott.content.entity.Content;
import com.communityott.content.repository.ContentRepository;
import com.communityott.saved.dto.SavedContentResponse;
import com.communityott.saved.dto.SavedStatusResponse;
import com.communityott.saved.entity.SavedContent;
import com.communityott.saved.repository.SavedContentRepository;
import com.communityott.user.entity.User;
import com.communityott.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class SavedContentService {

    private final SavedContentRepository savedContentRepository;
    private final ContentRepository contentRepository;
    private final UserRepository userRepository;

    /**
     * Adds content to user's saved list (idempotent).
     *
     * @param userId    Authenticated user ID
     * @param contentId Content ID to save
     * @return SavedContentResponse
     */
    @Transactional
    public SavedContentResponse addToMyList(Long userId, Long contentId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (contentId == null) {
            throw new IllegalArgumentException("Content ID cannot be null");
        }

        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new ContentNotFoundException(contentId));

        Optional<SavedContent> existing = savedContentRepository.findByUserIdAndContentId(userId, contentId);
        if (existing.isPresent()) {
            log.debug("Content ID {} is already in My List for user ID {}", contentId, userId);
            return SavedContentResponse.from(existing.get());
        }

        User userReference = userRepository.getReferenceById(userId);

        SavedContent savedContent = SavedContent.builder()
                .user(userReference)
                .content(content)
                .savedAt(Instant.now())
                .build();

        SavedContent saved = savedContentRepository.save(savedContent);
        log.info("Added content ID {} to My List for user ID {}", contentId, userId);
        return SavedContentResponse.from(saved);
    }

    /**
     * Removes content from user's saved list (idempotent).
     *
     * @param userId    Authenticated user ID
     * @param contentId Content ID to remove
     */
    @Transactional
    public void removeFromMyList(Long userId, Long contentId) {
        if (userId == null || contentId == null) {
            return;
        }

        savedContentRepository.deleteByUserIdAndContentId(userId, contentId);
        log.info("Removed content ID {} from My List for user ID {}", contentId, userId);
    }

    /**
     * Checks if content is currently saved in user's list.
     *
     * @param userId    Authenticated user ID
     * @param contentId Content ID to check
     * @return SavedStatusResponse
     */
    @Transactional(readOnly = true)
    public SavedStatusResponse isSaved(Long userId, Long contentId) {
        if (userId == null || contentId == null) {
            return SavedStatusResponse.builder().contentId(contentId).saved(false).build();
        }

        boolean exists = savedContentRepository.existsByUserIdAndContentId(userId, contentId);
        return SavedStatusResponse.builder()
                .contentId(contentId)
                .saved(exists)
                .build();
    }

    /**
     * Retrieves paginated saved content for the authenticated user.
     *
     * @param userId   Authenticated user ID
     * @param pageable Client requested pagination
     * @return Paginated list of SavedContentResponse
     */
    @Transactional(readOnly = true)
    public Page<SavedContentResponse> getMyList(Long userId, Pageable pageable) {
        if (userId == null) {
            return Page.empty(pageable);
        }

        int size = Math.min(pageable.getPageSize(), 50);
        int page = Math.max(0, pageable.getPageNumber());

        Pageable boundedPageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "savedAt"));
        Page<SavedContent> pageResult = savedContentRepository.findByUserIdWithContent(userId, boundedPageable);

        return pageResult.map(SavedContentResponse::from);
    }
}
