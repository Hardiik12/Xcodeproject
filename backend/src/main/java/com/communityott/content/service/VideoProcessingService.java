package com.communityott.content.service;

import com.communityott.common.exception.ActiveJobAlreadyExistsException;
import com.communityott.common.exception.InvalidJobStateTransitionException;
import com.communityott.common.exception.VideoAssetNotFoundException;
import com.communityott.common.exception.VideoProcessingJobNotFoundException;
import com.communityott.content.dto.VideoProcessingJobResponse;
import com.communityott.content.dto.VideoRenditionResponse;
import com.communityott.content.entity.ProcessingJobStatus;
import com.communityott.content.entity.ProcessingJobType;
import com.communityott.content.entity.VideoAsset;
import com.communityott.content.entity.VideoProcessingJob;
import com.communityott.content.processing.FFmpegProperties;
import com.communityott.content.processing.VideoProcessor;
import com.communityott.content.repository.VideoAssetRepository;
import com.communityott.content.repository.VideoProcessingJobRepository;
import com.communityott.content.repository.VideoRenditionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class VideoProcessingService {

    private final VideoProcessingJobRepository jobRepository;
    private final VideoAssetRepository videoAssetRepository;
    private final VideoRenditionRepository videoRenditionRepository;
    private final VideoProcessor videoProcessor;
    private final FFmpegProperties properties;

    @Qualifier("videoProcessingExecutor")
    private final Executor videoProcessingExecutor;

    @Transactional
    public VideoProcessingJobResponse createAndEnqueueProbeJob(Long videoAssetId, Long userId) {
        return createAndEnqueueJob(videoAssetId, ProcessingJobType.PROBE, userId);
    }

    @Transactional
    public VideoProcessingJobResponse createAndEnqueueTranscodeJob(Long videoAssetId, Long userId) {
        return createAndEnqueueJob(videoAssetId, ProcessingJobType.TRANSCODE, userId);
    }

    private VideoProcessingJobResponse createAndEnqueueJob(Long videoAssetId, ProcessingJobType jobType, Long userId) {
        log.info("Creating and enqueuing {} job for videoAssetId={}, requestedBy={}", jobType, videoAssetId, userId);

        VideoAsset videoAsset = videoAssetRepository.findById(videoAssetId)
                .orElseThrow(() -> new VideoAssetNotFoundException(videoAssetId));

        // Idempotency check: prevent duplicate active jobs for the same video asset and type
        Optional<VideoProcessingJob> activeJob = jobRepository.findActiveJob(
                videoAssetId,
                jobType,
                Set.of(ProcessingJobStatus.QUEUED, ProcessingJobStatus.PROCESSING)
        );

        if (activeJob.isPresent()) {
            log.warn("Active {} job ID: {} already exists for videoAssetId: {}", jobType, activeJob.get().getId(), videoAssetId);
            throw new ActiveJobAlreadyExistsException(videoAssetId, jobType.name());
        }

        VideoProcessingJob job = VideoProcessingJob.builder()
                .videoAsset(videoAsset)
                .jobType(jobType)
                .status(ProcessingJobStatus.QUEUED)
                .attemptCount(0)
                .maxAttempts(3)
                .priority(0)
                .createdBy(userId)
                .build();

        VideoProcessingJob savedJob = jobRepository.save(job);
        final Long jobId = savedJob.getId();

        // Submit to background processing worker
        videoProcessingExecutor.execute(() -> {
            try {
                videoProcessor.process(jobId);
            } catch (Exception e) {
                log.error("Unhandled worker exception processing jobId {}: {}", jobId, e.getMessage(), e);
            }
        });

        log.info("Successfully enqueued {} job ID: {} for videoAssetId: {}", jobType, jobId, videoAssetId);
        return VideoProcessingJobResponse.fromEntity(savedJob);
    }

    @Transactional
    public VideoProcessingJobResponse retryProcessingJob(Long jobId, Long userId) {
        log.info("Retrying processing job ID: {}, requestedBy: {}", jobId, userId);

        VideoProcessingJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new VideoProcessingJobNotFoundException(jobId));

        if (job.getStatus() != ProcessingJobStatus.FAILED) {
            throw new InvalidJobStateTransitionException("Only FAILED jobs can be retried (current status: " + job.getStatus() + ")");
        }

        if (!job.canRetry()) {
            throw new InvalidJobStateTransitionException("Maximum retry attempts reached (" + job.getMaxAttempts() + ")");
        }

        job.requeue();
        VideoProcessingJob savedJob = jobRepository.save(job);
        final Long retriedJobId = savedJob.getId();

        videoProcessingExecutor.execute(() -> {
            try {
                videoProcessor.process(retriedJobId);
            } catch (Exception e) {
                log.error("Unhandled worker exception retrying jobId {}: {}", retriedJobId, e.getMessage(), e);
            }
        });

        log.info("Successfully requeued processing job ID: {} (attempt {}/{})", retriedJobId, savedJob.getAttemptCount(), savedJob.getMaxAttempts());
        return VideoProcessingJobResponse.fromEntity(savedJob);
    }

    @Transactional(readOnly = true)
    public List<VideoProcessingJobResponse> getProcessingJobsForVideo(Long videoAssetId) {
        if (!videoAssetRepository.existsById(videoAssetId)) {
            throw new VideoAssetNotFoundException(videoAssetId);
        }
        return jobRepository.findByVideoAssetIdOrderByCreatedAtDesc(videoAssetId)
                .stream()
                .map(VideoProcessingJobResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public VideoProcessingJobResponse getJobById(Long jobId) {
        VideoProcessingJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new VideoProcessingJobNotFoundException(jobId));
        return VideoProcessingJobResponse.fromEntity(job);
    }

    @Transactional(readOnly = true)
    public List<VideoRenditionResponse> getRenditionsForVideo(Long videoAssetId) {
        if (!videoAssetRepository.existsById(videoAssetId)) {
            throw new VideoAssetNotFoundException(videoAssetId);
        }
        return videoRenditionRepository.findByVideoAssetIdOrderByHeightDesc(videoAssetId)
                .stream()
                .map(VideoRenditionResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public int recoverStaleJobs() {
        Instant threshold = Instant.now().minus(properties.getHeartbeatTimeoutSeconds(), ChronoUnit.SECONDS);
        List<VideoProcessingJob> staleJobs = jobRepository.findStaleProcessingJobs(ProcessingJobStatus.PROCESSING, threshold);
        int recoveredCount = 0;
        for (VideoProcessingJob job : staleJobs) {
            int currentAttempts = job.getAttemptCount() != null ? job.getAttemptCount() : 0;
            int maxAttempts = job.getMaxAttempts() != null ? job.getMaxAttempts() : 3;
            if (currentAttempts < maxAttempts) {
                log.warn("Recovering stale processing job ID {} (last heartbeat: {})", job.getId(), job.getLastHeartbeatAt());
                job.requeue();
                jobRepository.save(job);
                recoveredCount++;
                triggerAsyncProcessing(job.getId());
            } else {
                log.error("Stale job ID {} exceeded max attempts ({}/{}). Marking FAILED.", job.getId(), currentAttempts, maxAttempts);
                job.markFailed("MAX_ATTEMPTS_EXCEEDED", "Job exceeded maximum processing attempts after heartbeat timeout");
                jobRepository.save(job);
            }
        }
        return recoveredCount;
    }

    private void triggerAsyncProcessing(Long jobId) {
        videoProcessingExecutor.execute(() -> {
            try {
                videoProcessor.process(jobId);
            } catch (Exception e) {
                log.error("Unhandled worker exception recovering jobId {}: {}", jobId, e.getMessage(), e);
            }
        });
    }
}
