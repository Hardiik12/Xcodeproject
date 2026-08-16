package com.communityott.content.service;

import com.communityott.common.exception.ContentNotAvailableForPlaybackException;
import com.communityott.common.exception.VideoNotReadyException;
import com.communityott.content.entity.Content;
import com.communityott.content.entity.ContentStatus;
import com.communityott.content.entity.HlsPackageStatus;
import com.communityott.content.entity.VideoAsset;
import com.communityott.content.entity.VideoAssetStatus;
import com.communityott.content.entity.VideoHlsPackage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ContentAccessService {

    /**
     * Verifies that the given content item is published and eligible for consumer playback.
     *
     * @param content Content entity to check
     * @throws ContentNotAvailableForPlaybackException if content is draft, uploading, processing, or unpublished
     */
    public void validateContentPlayable(Content content) {
        if (content == null) {
            throw new ContentNotAvailableForPlaybackException("Content does not exist");
        }

        if (content.getStatus() != ContentStatus.PUBLISHED) {
            log.warn("Playback access denied: Content ID {} is in state '{}'", content.getId(), content.getStatus());
            throw new ContentNotAvailableForPlaybackException(content.getId(), content.getStatus().name());
        }
    }

    /**
     * Verifies that the video asset has completed all processing steps.
     *
     * @param videoAsset VideoAsset entity to check
     * @throws VideoNotReadyException if video asset is not in READY status
     */
    public void validateVideoAssetPlayable(VideoAsset videoAsset) {
        if (videoAsset == null) {
            throw new VideoNotReadyException("No video asset is associated with this content");
        }

        if (videoAsset.getStatus() != VideoAssetStatus.READY) {
            log.warn("Playback access denied: VideoAsset ID {} is in state '{}'", videoAsset.getId(), videoAsset.getStatus());
            throw new VideoNotReadyException(videoAsset.getId(), videoAsset.getStatus().name());
        }
    }

    /**
     * Verifies that the HLS package has been generated and validated.
     *
     * @param hlsPackage VideoHlsPackage entity to check
     * @throws VideoNotReadyException if HLS package is missing or not in READY status
     */
    public void validateHlsPackagePlayable(VideoHlsPackage hlsPackage) {
        if (hlsPackage == null) {
            throw new VideoNotReadyException("HLS package has not been created for this video asset");
        }

        if (hlsPackage.getStatus() != HlsPackageStatus.READY) {
            log.warn("Playback access denied: HlsPackage ID {} is in state '{}'", hlsPackage.getId(), hlsPackage.getStatus());
            throw new VideoNotReadyException("HLS package is in status: " + hlsPackage.getStatus());
        }

        if (hlsPackage.getMasterPlaylistKey() == null || hlsPackage.getMasterPlaylistKey().isBlank()) {
            throw new VideoNotReadyException("HLS master playlist key is missing");
        }
    }
}
