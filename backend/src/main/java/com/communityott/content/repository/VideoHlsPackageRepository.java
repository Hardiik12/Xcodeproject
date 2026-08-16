package com.communityott.content.repository;

import com.communityott.content.entity.HlsPackageStatus;
import com.communityott.content.entity.VideoHlsPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VideoHlsPackageRepository extends JpaRepository<VideoHlsPackage, Long> {

    Optional<VideoHlsPackage> findByVideoAssetId(Long videoAssetId);

    @Query("SELECT p FROM VideoHlsPackage p LEFT JOIN FETCH p.variants WHERE p.videoAsset.id = :videoAssetId")
    Optional<VideoHlsPackage> findByVideoAssetIdWithVariants(@Param("videoAssetId") Long videoAssetId);

    boolean existsByVideoAssetIdAndStatus(Long videoAssetId, HlsPackageStatus status);

    void deleteByVideoAssetId(Long videoAssetId);
}
