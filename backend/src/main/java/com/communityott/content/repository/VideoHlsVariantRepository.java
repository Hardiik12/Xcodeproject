package com.communityott.content.repository;

import com.communityott.content.entity.VideoHlsVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VideoHlsVariantRepository extends JpaRepository<VideoHlsVariant, Long> {

    List<VideoHlsVariant> findByHlsPackageIdOrderByHeightDesc(Long hlsPackageId);

    Optional<VideoHlsVariant> findByHlsPackageIdAndResolution(Long hlsPackageId, String resolution);

    void deleteByHlsPackageId(Long hlsPackageId);
}
