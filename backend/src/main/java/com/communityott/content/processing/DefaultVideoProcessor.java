package com.communityott.content.processing;

import com.communityott.common.exception.VideoProcessingJobNotFoundException;
import com.communityott.content.entity.Content;
import com.communityott.content.entity.ContentStatus;
import com.communityott.content.entity.ProcessingJobStatus;
import com.communityott.content.entity.VideoAsset;
import com.communityott.content.entity.VideoAssetStatus;
import com.communityott.content.entity.VideoProcessingJob;
import com.communityott.content.repository.ContentRepository;
import com.communityott.content.repository.VideoAssetRepository;
import com.communityott.content.repository.VideoProcessingJobRepository;
import com.communityott.content.storage.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class DefaultVideoProcessor implements VideoProcessor {

    private final VideoProcessingJobRepository jobRepository;
    private final VideoAssetRepository videoAssetRepository;
    private final ContentRepository contentRepository;
    private final ObjectStorageService objectStorageService;
    private final FFprobeService ffprobeService;
    private final FFmpegProperties properties;

    @Override
    public void process(Long jobId) {
        String workerId = getWorkerIdentifier();
        log.info("Worker [{}] starting processing for VideoProcessingJob ID: {}", workerId, jobId);

        VideoProcessingJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new VideoProcessingJobNotFoundException(jobId));

        if (job.getStatus() != ProcessingJobStatus.QUEUED) {
            log.warn("Job ID: {} is not in QUEUED state (current: {}). Skipping processing.", jobId, job.getStatus());
            return;
        }

        // 1. Mark job as PROCESSING
        markJobProcessing(jobId, workerId);

        File tempDir = null;
        try {
            // Re-fetch with eager associations
            job = jobRepository.findByIdWithAssetAndContent(jobId)
                    .orElseThrow(() -> new VideoProcessingJobNotFoundException(jobId));
            VideoAsset asset = job.getVideoAsset();
            Content content = asset.getContent();

            // 2. Prepare local scratch workspace
            tempDir = createTempWorkingDir(jobId);
            String safeExt = getSafeExtension(asset.getOriginalFilename());
            File sourceLocalFile = new File(tempDir, "source_input" + safeExt);

            // 3. Download source video from MinIO
            log.debug("Downloading source object from MinIO: bucket={}, key={}", asset.getStorageBucket(), asset.getStorageKey());
            try (InputStream minioStream = new BufferedInputStream(objectStorageService.getObject(asset.getStorageBucket(), asset.getStorageKey()));
                 OutputStream fileOut = new BufferedOutputStream(new FileOutputStream(sourceLocalFile))) {
                minioStream.transferTo(fileOut);
            }

            // 4. Execute FFprobe inspection
            MediaProbeResult probeResult = ffprobeService.probe(sourceLocalFile);

            if (!probeResult.isValidMedia()) {
                log.error("Media probing failed for jobId={}, assetId={}: {}", jobId, asset.getId(), probeResult.getValidationError());
                markJobFailed(jobId, "MEDIA_VALIDATION_FAILED", probeResult.getValidationError());
                return;
            }

            // 5. Success path: update domain models
            markJobSuccess(jobId, probeResult);
            log.info("Successfully completed probe processing for jobId={}, assetId={}", jobId, asset.getId());

        } catch (Exception e) {
            log.error("Unexpected failure during processing for jobId={}: {}", jobId, e.getMessage(), e);
            markJobFailed(jobId, "PROCESSING_EXECUTION_ERROR", e.getMessage());
        } finally {
            // 6. Scratch file cleanup in ALL execution paths
            cleanupDirectory(tempDir);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markJobProcessing(Long jobId, String workerId) {
        VideoProcessingJob job = jobRepository.findByIdWithAssetAndContent(jobId)
                .orElseThrow(() -> new VideoProcessingJobNotFoundException(jobId));
        job.markProcessing(workerId);
        jobRepository.save(job);

        VideoAsset asset = job.getVideoAsset();
        asset.setStatus(VideoAssetStatus.PROCESSING);
        videoAssetRepository.save(asset);

        Content content = asset.getContent();
        if (content.getStatus() == ContentStatus.DRAFT || content.getStatus() == ContentStatus.UPLOADING) {
            content.setStatus(ContentStatus.PROCESSING);
            contentRepository.save(content);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markJobSuccess(Long jobId, MediaProbeResult probeResult) {
        VideoProcessingJob job = jobRepository.findByIdWithAssetAndContent(jobId)
                .orElseThrow(() -> new VideoProcessingJobNotFoundException(jobId));
        job.markCompleted(probeResult.getRawJson());
        jobRepository.save(job);

        VideoAsset asset = job.getVideoAsset();
        asset.setDurationSeconds(probeResult.getDurationSeconds());
        asset.setWidth(probeResult.getWidth());
        asset.setHeight(probeResult.getHeight());
        asset.setBitrateKbps(probeResult.getBitrateKbps());
        asset.setStatus(VideoAssetStatus.READY);
        videoAssetRepository.save(asset);

        Content content = asset.getContent();
        if (content.getDurationSeconds() == null || content.getDurationSeconds() <= 0) {
            content.setDurationSeconds(probeResult.getDurationSeconds());
        }
        if (content.getStatus() == ContentStatus.PROCESSING || content.getStatus() == ContentStatus.UPLOADING || content.getStatus() == ContentStatus.DRAFT) {
            content.setStatus(ContentStatus.READY);
        }
        contentRepository.save(content);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markJobFailed(Long jobId, String errorCode, String errorMessage) {
        VideoProcessingJob job = jobRepository.findByIdWithAssetAndContent(jobId)
                .orElseThrow(() -> new VideoProcessingJobNotFoundException(jobId));
        job.markFailed(errorCode, errorMessage);
        jobRepository.save(job);

        VideoAsset asset = job.getVideoAsset();
        asset.setStatus(VideoAssetStatus.FAILED);
        videoAssetRepository.save(asset);

        Content content = asset.getContent();
        if (content.getStatus() == ContentStatus.PROCESSING || content.getStatus() == ContentStatus.UPLOADING) {
            content.setStatus(ContentStatus.FAILED);
            contentRepository.save(content);
        }
    }

    private File createTempWorkingDir(Long jobId) {
        File baseDir = new File(properties.getTempDir());
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
        String dirName = "job_" + jobId + "_" + UUID.randomUUID().toString().substring(0, 8);
        File tempDir = new File(baseDir, dirName);
        tempDir.mkdirs();
        return tempDir;
    }

    private void cleanupDirectory(File dir) {
        if (dir == null || !dir.exists()) {
            return;
        }
        try {
            Files.walk(dir.toPath())
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            log.debug("Cleaned up temporary workspace directory: {}", dir.getAbsolutePath());
        } catch (Exception e) {
            log.warn("Failed to delete temp workspace directory {}: {}", dir.getAbsolutePath(), e.getMessage());
        }
    }

    private String getSafeExtension(String originalFilename) {
        if (originalFilename != null && originalFilename.contains(".")) {
            String ext = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
            if (ext.matches("^\\.[a-z0-9]{2,5}$")) {
                return ext;
            }
        }
        return ".mp4";
    }

    private String getWorkerIdentifier() {
        return ManagementFactory.getRuntimeMXBean().getName() + "-" + Thread.currentThread().getName();
    }
}
