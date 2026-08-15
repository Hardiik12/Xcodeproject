package com.communityott.content.repository;

import com.communityott.content.entity.ProcessingJobStatus;
import com.communityott.content.entity.ProcessingJobType;
import com.communityott.content.entity.VideoProcessingJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface VideoProcessingJobRepository extends JpaRepository<VideoProcessingJob, Long> {

    List<VideoProcessingJob> findByVideoAssetIdOrderByCreatedAtDesc(Long videoAssetId);

    @Query("SELECT j FROM VideoProcessingJob j JOIN FETCH j.videoAsset va JOIN FETCH va.content WHERE j.id = :id")
    Optional<VideoProcessingJob> findByIdWithAssetAndContent(@Param("id") Long id);

    @Query("SELECT j FROM VideoProcessingJob j WHERE j.videoAsset.id = :videoAssetId AND j.jobType = :jobType AND j.status IN :statuses")
    Optional<VideoProcessingJob> findActiveJob(
            @Param("videoAssetId") Long videoAssetId,
            @Param("jobType") ProcessingJobType jobType,
            @Param("statuses") Collection<ProcessingJobStatus> statuses
    );

    List<VideoProcessingJob> findByStatus(ProcessingJobStatus status);

    @Query("SELECT j FROM VideoProcessingJob j WHERE j.status = :status AND (j.lastHeartbeatAt IS NULL OR j.lastHeartbeatAt < :threshold)")
    List<VideoProcessingJob> findStaleProcessingJobs(
            @Param("status") ProcessingJobStatus status,
            @Param("threshold") Instant threshold
    );

    @Query("SELECT j FROM VideoProcessingJob j WHERE j.status = 'QUEUED' ORDER BY j.priority DESC, j.createdAt ASC")
    List<VideoProcessingJob> findPendingQueuedJobs();
}
