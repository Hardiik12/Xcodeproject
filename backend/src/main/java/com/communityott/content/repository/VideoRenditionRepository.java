package com.communityott.content.repository;

import com.communityott.content.entity.VideoRendition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VideoRenditionRepository extends JpaRepository<VideoRendition, Long> {

    List<VideoRendition> findByVideoAssetIdOrderByHeightDesc(Long videoAssetId);

    Optional<VideoRendition> findByVideoAssetIdAndResolution(Long videoAssetId, String resolution);

    void deleteByVideoAssetId(Long videoAssetId);

    @Query("SELECT r FROM VideoRendition r WHERE r.videoAsset.id = :videoAssetId AND r.status = 'READY' ORDER BY r.height DESC")
    List<VideoRendition> findReadyRenditionsByAssetId(@Param("videoAssetId") Long videoAssetId);
}
