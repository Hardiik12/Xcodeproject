package com.communityott.playback.service;

import com.communityott.playback.config.PlaybackProperties;
import com.communityott.playback.dto.ContinueWatchingItemResponse;
import com.communityott.playback.entity.WatchProgress;
import com.communityott.playback.repository.WatchProgressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ContinueWatchingService {

    private final WatchProgressRepository watchProgressRepository;
    private final PlaybackProperties playbackProperties;

    /**
     * Retrieves paginated Continue Watching items for the authenticated user.
     *
     * <p>Filters for in-progress content (position > 0, completion < 95%, completed == false)
     * where the content is currently published and has ready HLS streaming packages.</p>
     *
     * @param userId   Authenticated user ID
     * @param pageable Client requested pagination
     * @return Paginated list of ContinueWatchingItemResponse
     */
    @Transactional(readOnly = true)
    public Page<ContinueWatchingItemResponse> getContinueWatching(Long userId, Pageable pageable) {
        if (userId == null) {
            return Page.empty(pageable);
        }

        // Limit maximum page size to 50
        int size = Math.min(pageable.getPageSize(), 50);
        int page = Math.max(0, pageable.getPageNumber());

        Pageable boundedPageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "lastWatchedAt"));
        Page<WatchProgress> progressPage = watchProgressRepository.findContinueWatchingForUser(userId, boundedPageable);

        return progressPage.map(ContinueWatchingItemResponse::from);
    }
}
