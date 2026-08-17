package com.communityott.playback.repository;

import com.communityott.playback.entity.WatchProgress;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WatchProgressRepository extends JpaRepository<WatchProgress, Long> {

    Optional<WatchProgress> findByUserIdAndContentId(Long userId, Long contentId);

    List<WatchProgress> findByUserIdOrderByLastWatchedAtDesc(Long userId);

    @Query(value = """
            SELECT wp FROM WatchProgress wp
            JOIN FETCH wp.content c
            LEFT JOIN FETCH wp.videoAsset va
            WHERE wp.user.id = :userId
              AND wp.positionSeconds > 0
              AND wp.completionPercentage < 95.0
              AND c.status = com.communityott.content.entity.ContentStatus.PUBLISHED
              AND EXISTS (
                  SELECT 1 FROM VideoAsset a
                  JOIN VideoHlsPackage p ON p.videoAsset.id = a.id
                  WHERE a.content.id = c.id
                    AND a.status = com.communityott.content.entity.VideoAssetStatus.READY
                    AND p.status = com.communityott.content.entity.HlsPackageStatus.READY
              )
            """,
            countQuery = """
            SELECT count(wp) FROM WatchProgress wp
            JOIN wp.content c
            WHERE wp.user.id = :userId
              AND wp.positionSeconds > 0
              AND wp.completionPercentage < 95.0
              AND c.status = com.communityott.content.entity.ContentStatus.PUBLISHED
              AND EXISTS (
                  SELECT 1 FROM VideoAsset a
                  JOIN VideoHlsPackage p ON p.videoAsset.id = a.id
                  WHERE a.content.id = c.id
                    AND a.status = com.communityott.content.entity.VideoAssetStatus.READY
                    AND p.status = com.communityott.content.entity.HlsPackageStatus.READY
              )
            """)
    Page<WatchProgress> findContinueWatchingForUser(@Param("userId") Long userId, Pageable pageable);
}
