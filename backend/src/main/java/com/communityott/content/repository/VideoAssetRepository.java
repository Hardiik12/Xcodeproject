package com.communityott.content.repository;

import com.communityott.content.entity.VideoAsset;
import com.communityott.content.entity.VideoAssetStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VideoAssetRepository extends JpaRepository<VideoAsset, Long> {

    List<VideoAsset> findByContentIdOrderByCreatedAtDesc(Long contentId);

    List<VideoAsset> findByContentIdAndStatus(Long contentId, VideoAssetStatus status);

    Optional<VideoAsset> findByIdAndContentId(Long id, Long contentId);

    Optional<VideoAsset> findByStorageKey(String storageKey);

    boolean existsByChecksumSha256(String checksumSha256);
}
