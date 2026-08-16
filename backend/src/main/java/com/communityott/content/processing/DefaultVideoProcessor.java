package com.communityott.content.processing;

import com.communityott.common.exception.VideoProcessingException;
import com.communityott.common.exception.VideoProcessingJobNotFoundException;
import com.communityott.content.entity.Content;
import com.communityott.content.entity.ContentStatus;
import com.communityott.content.entity.ProcessingJobStatus;
import com.communityott.content.entity.ProcessingJobType;
import com.communityott.content.entity.RenditionStatus;
import com.communityott.content.entity.VideoAsset;
import com.communityott.content.entity.VideoAssetStatus;
import com.communityott.content.entity.VideoProcessingJob;
import com.communityott.content.entity.VideoRendition;
import com.communityott.content.entity.VideoResolution;
import com.communityott.content.repository.ContentRepository;
import com.communityott.content.repository.VideoAssetRepository;
import com.communityott.content.repository.VideoProcessingJobRepository;
import com.communityott.content.repository.VideoRenditionRepository;
import com.communityott.content.storage.ChecksumUtility;
import com.communityott.content.storage.ObjectStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class DefaultVideoProcessor implements VideoProcessor {

    private final VideoProcessingJobRepository jobRepository;
    private final VideoAssetRepository videoAssetRepository;
    private final VideoRenditionRepository videoRenditionRepository;
    private final ContentRepository contentRepository;
    private final ObjectStorageService objectStorageService;
    private final FFprobeService ffprobeService;
    private final FFmpegTranscodeService ffmpegTranscodeService;
    private final FFmpegProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

            // Update probed metadata on asset
            updateProbedMetadata(asset.getId(), probeResult);

            // 5. Multi-resolution Transcoding Ladder
            int sourceHeight = probeResult.getHeight() != null && probeResult.getHeight() > 0 ? probeResult.getHeight() : 1080;
            List<VideoResolution> ladder = VideoResolution.getLadderForSource(sourceHeight);
            log.info("Determined transcoding ladder for assetId={} (source height: {}p): {}",
                    asset.getId(), sourceHeight, ladder);

            List<Map<String, Object>> generatedRenditions = new ArrayList<>();

            for (VideoResolution res : ladder) {
                File renditionLocalFile = new File(tempDir, "rendition_" + res.getLabel() + ".mp4");
                TranscodeProfile profile = TranscodeProfile.fromResolution(res);

                log.info("Transcoding rendition [{}] for assetId={}, jobId={}", res.getLabel(), asset.getId(), jobId);
                boolean success = ffmpegTranscodeService.transcode(sourceLocalFile, renditionLocalFile, profile);

                if (!success || !renditionLocalFile.exists() || renditionLocalFile.length() == 0) {
                    throw new VideoProcessingException("Failed to generate rendition " + res.getLabel());
                }

                // Calculate SHA-256 for rendition
                String checksum = ChecksumUtility.calculateSha256(renditionLocalFile);
                long fileSize = renditionLocalFile.length();
                String storageKey = "renditions/asset_" + asset.getId() + "/" + res.getLabel() + ".mp4";

                // Upload rendition to MinIO
                log.info("Uploading rendition [{}] to MinIO (key: {}, size: {} bytes)", res.getLabel(), storageKey, fileSize);
                try (InputStream renditionIn = new BufferedInputStream(new FileInputStream(renditionLocalFile))) {
                    objectStorageService.uploadObject(asset.getStorageBucket(), storageKey, renditionIn, fileSize, "video/mp4");
                }

                // Persist VideoRendition metadata to database
                saveOrUpdateRendition(asset.getId(), res, profile, storageKey, asset.getStorageBucket(),
                        fileSize, checksum, probeResult.getDurationSeconds(), parseFrameRate(probeResult.getFrameRate()));

                Map<String, Object> summary = new HashMap<>();
                summary.put("resolution", res.getLabel());
                summary.put("storageKey", storageKey);
                summary.put("fileSizeBytes", fileSize);
                summary.put("checksumSha256", checksum);
                generatedRenditions.add(summary);
            }

            // 6. Success path: update domain models
            Map<String, Object> finalPayload = new HashMap<>();
            finalPayload.put("probed", probeResult);
            finalPayload.put("renditions", generatedRenditions);
            String payloadJson = objectMapper.writeValueAsString(finalPayload);

            markJobSuccess(jobId, payloadJson, probeResult.getDurationSeconds());
            log.info("Successfully completed transcoding pipeline for jobId={}, assetId={}, generated {} renditions",
                    jobId, asset.getId(), generatedRenditions.size());

        } catch (Exception e) {
            log.error("Unexpected failure during processing for jobId={}: {}", jobId, e.getMessage(), e);
            markJobFailed(jobId, "PROCESSING_EXECUTION_ERROR", e.getMessage());
        } finally {
            // 7. Scratch file cleanup in ALL execution paths
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
    public void updateProbedMetadata(Long assetId, MediaProbeResult probeResult) {
        VideoAsset asset = videoAssetRepository.findById(assetId)
                .orElseThrow(() -> new VideoProcessingException("Asset not found for id: " + assetId));
        asset.setDurationSeconds(probeResult.getDurationSeconds());
        asset.setWidth(probeResult.getWidth());
        asset.setHeight(probeResult.getHeight());
        asset.setBitrateKbps(probeResult.getBitrateKbps());
        videoAssetRepository.save(asset);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveOrUpdateRendition(Long assetId, VideoResolution res, TranscodeProfile profile,
                                      String storageKey, String storageBucket, long fileSizeBytes,
                                      String checksumSha256, Integer durationSeconds, Double frameRate) {
        VideoAsset asset = videoAssetRepository.findById(assetId)
                .orElseThrow(() -> new VideoProcessingException("Asset not found for id: " + assetId));

        VideoRendition rendition = videoRenditionRepository
                .findByVideoAssetIdAndResolution(assetId, res.getLabel())
                .orElse(VideoRendition.builder()
                        .videoAsset(asset)
                        .resolution(res.getLabel())
                        .build());

        rendition.setWidth(res.getWidth());
        rendition.setHeight(res.getHeight());
        rendition.setVideoCodec("h264");
        rendition.setAudioCodec("aac");
        rendition.setBitrateKbps(res.getVideoBitrateKbps());
        rendition.setAudioBitrateKbps(res.getAudioBitrateKbps());
        rendition.setFrameRate(frameRate != null ? frameRate : 30.0);
        rendition.setFileSizeBytes(fileSizeBytes);
        rendition.setStorageBucket(storageBucket);
        rendition.setStorageKey(storageKey);
        rendition.setChecksumSha256(checksumSha256);
        rendition.setDurationSeconds(durationSeconds);
        rendition.setStatus(RenditionStatus.READY);

        videoRenditionRepository.save(rendition);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markJobSuccess(Long jobId, String payloadJson, Integer durationSeconds) {
        VideoProcessingJob job = jobRepository.findByIdWithAssetAndContent(jobId)
                .orElseThrow(() -> new VideoProcessingJobNotFoundException(jobId));
        job.markCompleted(payloadJson);
        jobRepository.save(job);

        VideoAsset asset = job.getVideoAsset();
        asset.setStatus(VideoAssetStatus.READY);
        videoAssetRepository.save(asset);

        Content content = asset.getContent();
        if (content.getDurationSeconds() == null || content.getDurationSeconds() <= 0) {
            content.setDurationSeconds(durationSeconds);
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

    private Double parseFrameRate(String frameRateStr) {
        if (frameRateStr == null || frameRateStr.isBlank()) {
            return 30.0;
        }
        try {
            if (frameRateStr.contains("/")) {
                String[] parts = frameRateStr.split("/");
                double num = Double.parseDouble(parts[0].trim());
                double den = Double.parseDouble(parts[1].trim());
                return den != 0 ? num / den : 30.0;
            }
            return Double.parseDouble(frameRateStr.trim());
        } catch (Exception e) {
            return 30.0;
        }
    }

    private String getWorkerIdentifier() {
        return ManagementFactory.getRuntimeMXBean().getName() + "-" + Thread.currentThread().getName();
    }
}
