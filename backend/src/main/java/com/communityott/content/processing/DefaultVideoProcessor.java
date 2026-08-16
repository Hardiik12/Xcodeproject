package com.communityott.content.processing;

import com.communityott.common.exception.VideoProcessingException;
import com.communityott.common.exception.VideoProcessingJobNotFoundException;
import com.communityott.content.entity.Content;
import com.communityott.content.entity.ContentStatus;
import com.communityott.content.entity.HlsPackageStatus;
import com.communityott.content.entity.HlsVariantStatus;
import com.communityott.content.entity.ProcessingJobStatus;
import com.communityott.content.entity.ProcessingJobType;
import com.communityott.content.entity.RenditionStatus;
import com.communityott.content.entity.VideoAsset;
import com.communityott.content.entity.VideoAssetStatus;
import com.communityott.content.entity.VideoHlsPackage;
import com.communityott.content.entity.VideoHlsVariant;
import com.communityott.content.entity.VideoProcessingJob;
import com.communityott.content.entity.VideoRendition;
import com.communityott.content.entity.VideoResolution;
import com.communityott.content.repository.ContentRepository;
import com.communityott.content.repository.VideoAssetRepository;
import com.communityott.content.repository.VideoHlsPackageRepository;
import com.communityott.content.repository.VideoHlsVariantRepository;
import com.communityott.content.repository.VideoProcessingJobRepository;
import com.communityott.content.repository.VideoRenditionRepository;
import com.communityott.content.storage.ChecksumUtility;
import com.communityott.content.storage.ObjectStorageService;
import com.communityott.content.storage.StorageKeyGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
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
    private final VideoHlsPackageRepository videoHlsPackageRepository;
    private final VideoHlsVariantRepository videoHlsVariantRepository;
    private final ContentRepository contentRepository;
    private final ObjectStorageService objectStorageService;
    private final FFprobeService ffprobeService;
    private final FFmpegTranscodeService ffmpegTranscodeService;
    private final FFmpegHlsPackagingService ffmpegHlsPackagingService;
    private final HlsManifestGenerator hlsManifestGenerator;
    private final HlsPackageValidator hlsPackageValidator;
    private final StorageKeyGenerator storageKeyGenerator;
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
            tempDir = createTempWorkingDir(jobId);

            if (job.getJobType() == ProcessingJobType.PACKAGE_HLS) {
                processHlsPackagingJob(job, asset, tempDir);
            } else {
                processTranscodingAndHlsJob(job, asset, tempDir);
            }

        } catch (Exception e) {
            log.error("Unexpected failure during processing for jobId={}: {}", jobId, e.getMessage(), e);
            markJobFailed(jobId, "PROCESSING_EXECUTION_ERROR", e.getMessage());
        } finally {
            // Scratch file cleanup in ALL execution paths
            cleanupDirectory(tempDir);
        }
    }

    private void processTranscodingAndHlsJob(VideoProcessingJob job, VideoAsset asset, File tempDir) throws Exception {
        Long jobId = job.getId();
        String safeExt = getSafeExtension(asset.getOriginalFilename());
        File sourceLocalFile = new File(tempDir, "source_input" + safeExt);

        // 1. Download source video from MinIO
        log.debug("Downloading source object from MinIO: bucket={}, key={}", asset.getStorageBucket(), asset.getStorageKey());
        try (InputStream minioStream = new BufferedInputStream(objectStorageService.getObject(asset.getStorageBucket(), asset.getStorageKey()));
             OutputStream fileOut = new BufferedOutputStream(new FileOutputStream(sourceLocalFile))) {
            minioStream.transferTo(fileOut);
        }

        // 2. Execute FFprobe inspection
        MediaProbeResult probeResult = ffprobeService.probe(sourceLocalFile);
        if (!probeResult.isValidMedia()) {
            log.error("Media probing failed for jobId={}, assetId={}: {}", jobId, asset.getId(), probeResult.getValidationError());
            markJobFailed(jobId, "MEDIA_VALIDATION_FAILED", probeResult.getValidationError());
            return;
        }

        updateProbedMetadata(asset.getId(), probeResult);

        // 3. Multi-resolution Transcoding Ladder
        int sourceHeight = probeResult.getHeight() != null && probeResult.getHeight() > 0 ? probeResult.getHeight() : 1080;
        List<VideoResolution> ladder = VideoResolution.getLadderForSource(sourceHeight);
        log.info("Determined transcoding ladder for assetId={} (source height: {}p): {}", asset.getId(), sourceHeight, ladder);

        List<Map<String, Object>> generatedRenditions = new ArrayList<>();
        List<VideoRendition> readyRenditions = new ArrayList<>();

        for (VideoResolution res : ladder) {
            File renditionLocalFile = new File(tempDir, "rendition_" + res.getLabel() + ".mp4");
            TranscodeProfile profile = TranscodeProfile.fromResolution(res);

            log.info("Transcoding rendition [{}] for assetId={}, jobId={}", res.getLabel(), asset.getId(), jobId);
            boolean success = ffmpegTranscodeService.transcode(sourceLocalFile, renditionLocalFile, profile);

            if (!success || !renditionLocalFile.exists() || renditionLocalFile.length() == 0) {
                log.warn("Failed to generate rendition {}, continuing with remaining ladder", res.getLabel());
                continue;
            }

            String checksum = ChecksumUtility.calculateSha256(renditionLocalFile);
            long fileSize = renditionLocalFile.length();
            String storageKey = "renditions/asset_" + asset.getId() + "/" + res.getLabel() + ".mp4";

            // Upload rendition to MinIO
            try (InputStream renditionIn = new BufferedInputStream(new FileInputStream(renditionLocalFile))) {
                objectStorageService.uploadObject(asset.getStorageBucket(), storageKey, renditionIn, fileSize, "video/mp4");
            }

            VideoRendition savedRendition = saveOrUpdateRendition(asset.getId(), res, profile, storageKey, asset.getStorageBucket(),
                    fileSize, checksum, probeResult.getDurationSeconds(), parseFrameRate(probeResult.getFrameRate()));
            readyRenditions.add(savedRendition);

            Map<String, Object> summary = new HashMap<>();
            summary.put("resolution", res.getLabel());
            summary.put("storageKey", storageKey);
            summary.put("fileSizeBytes", fileSize);
            summary.put("checksumSha256", checksum);
            generatedRenditions.add(summary);
        }

        if (readyRenditions.isEmpty()) {
            throw new VideoProcessingException("All transcoded renditions failed for assetId=" + asset.getId());
        }

        // 4. Execute HLS Packaging step
        VideoHlsPackage hlsPackage = executeHlsPackaging(asset, readyRenditions, tempDir, jobId);

        // 5. Success summary payload
        Map<String, Object> finalPayload = new HashMap<>();
        finalPayload.put("probed", probeResult);
        finalPayload.put("renditions", generatedRenditions);
        finalPayload.put("hlsMasterPlaylistKey", hlsPackage.getMasterPlaylistKey());
        finalPayload.put("hlsVariantsCount", hlsPackage.getVariantCount());
        String payloadJson = objectMapper.writeValueAsString(finalPayload);

        markJobSuccess(jobId, payloadJson, probeResult.getDurationSeconds());
        log.info("Successfully completed transcoding & HLS pipeline for jobId={}, assetId={}, generated {} renditions & HLS package",
                jobId, asset.getId(), generatedRenditions.size());
    }

    private void processHlsPackagingJob(VideoProcessingJob job, VideoAsset asset, File tempDir) throws Exception {
        Long jobId = job.getId();
        List<VideoRendition> readyRenditions = videoRenditionRepository.findByVideoAssetIdOrderByHeightDesc(asset.getId());

        readyRenditions = readyRenditions.stream()
                .filter(r -> r.getStatus() == RenditionStatus.READY)
                .toList();

        if (readyRenditions.isEmpty()) {
            throw new VideoProcessingException("No READY renditions found to package HLS for assetId=" + asset.getId());
        }

        VideoHlsPackage hlsPackage = executeHlsPackaging(asset, readyRenditions, tempDir, jobId);

        Map<String, Object> summary = new HashMap<>();
        summary.put("hlsMasterPlaylistKey", hlsPackage.getMasterPlaylistKey());
        summary.put("hlsVariantsCount", hlsPackage.getVariantCount());
        String payloadJson = objectMapper.writeValueAsString(summary);

        markJobSuccess(jobId, payloadJson, asset.getDurationSeconds());
        log.info("Successfully completed HLS packaging job for jobId={}, assetId={}", jobId, asset.getId());
    }

    public VideoHlsPackage executeHlsPackaging(VideoAsset asset, List<VideoRendition> renditions, File tempDir, Long jobId) throws Exception {
        Long contentId = asset.getContent().getId();
        Long assetId = asset.getId();
        String bucket = asset.getStorageBucket();

        log.info("Executing HLS packaging for assetId={}, contentId={}, renditionsCount={}", assetId, contentId, renditions.size());

        List<VideoHlsVariant> variantsToSave = new ArrayList<>();
        File hlsTempDir = new File(tempDir, "hls_output");
        hlsTempDir.mkdirs();

        for (VideoRendition rendition : renditions) {
            File renditionLocalFile = new File(tempDir, "rendition_" + rendition.getResolution() + ".mp4");
            if (!renditionLocalFile.exists()) {
                // Download from MinIO if not already in local temp directory
                try (InputStream minioIn = new BufferedInputStream(objectStorageService.getObject(bucket, rendition.getStorageKey()));
                     OutputStream fileOut = new BufferedOutputStream(new FileOutputStream(renditionLocalFile))) {
                    minioIn.transferTo(fileOut);
                }
            }

            File variantOutputDir = new File(hlsTempDir, rendition.getResolution());
            variantOutputDir.mkdirs();

            HlsPackagingResult packagingResult = ffmpegHlsPackagingService.packageToHls(
                    renditionLocalFile,
                    variantOutputDir,
                    rendition,
                    2
            );

            // Validate variant output integrity
            hlsPackageValidator.validateVariantPackage(
                    variantOutputDir,
                    packagingResult.getPlaylistFile(),
                    packagingResult.getInitSegmentFile(),
                    packagingResult.getMediaSegmentFiles()
            );

            // Upload variant files to MinIO
            String playlistKey = storageKeyGenerator.generateHlsVariantPlaylistKey(contentId, assetId, rendition.getResolution());
            String initKey = storageKeyGenerator.generateHlsInitSegmentKey(contentId, assetId, rendition.getResolution());

            try (InputStream playlistIn = new BufferedInputStream(new FileInputStream(packagingResult.getPlaylistFile()))) {
                objectStorageService.uploadObject(bucket, playlistKey, playlistIn, packagingResult.getPlaylistFile().length(), "application/vnd.apple.mpegurl");
            }

            try (InputStream initIn = new BufferedInputStream(new FileInputStream(packagingResult.getInitSegmentFile()))) {
                objectStorageService.uploadObject(bucket, initKey, initIn, packagingResult.getInitSegmentFile().length(), "video/mp4");
            }

            for (File segmentFile : packagingResult.getMediaSegmentFiles()) {
                String segmentKey = storageKeyGenerator.generateHlsMediaSegmentKey(contentId, assetId, rendition.getResolution(), segmentFile.getName());
                try (InputStream segmentIn = new BufferedInputStream(new FileInputStream(segmentFile))) {
                    objectStorageService.uploadObject(bucket, segmentKey, segmentIn, segmentFile.length(), "video/iso.segment");
                }
            }

            VideoHlsVariant variant = VideoHlsVariant.builder()
                    .videoRendition(rendition)
                    .resolution(rendition.getResolution())
                    .width(packagingResult.getWidth())
                    .height(packagingResult.getHeight())
                    .playlistKey(playlistKey)
                    .initSegmentKey(initKey)
                    .segmentCount(packagingResult.getSegmentCount())
                    .targetDurationSeconds(packagingResult.getTargetDurationSeconds())
                    .bandwidthBps(packagingResult.getBandwidthBps())
                    .averageBandwidthBps(packagingResult.getAverageBandwidthBps())
                    .codecs(packagingResult.getCodecs())
                    .frameRate(packagingResult.getFrameRate())
                    .status(HlsVariantStatus.READY)
                    .build();

            variantsToSave.add(variant);
        }

        // Generate Master Playlist
        String masterPlaylistContent = hlsManifestGenerator.generateMasterPlaylist(variantsToSave);
        hlsPackageValidator.validateMasterPlaylist(masterPlaylistContent, variantsToSave.size());

        String masterKey = storageKeyGenerator.generateHlsMasterKey(contentId, assetId);
        byte[] masterBytes = masterPlaylistContent.getBytes(StandardCharsets.UTF_8);

        try (InputStream masterIn = new ByteArrayInputStream(masterBytes)) {
            objectStorageService.uploadObject(bucket, masterKey, masterIn, masterBytes.length, "application/vnd.apple.mpegurl");
        }

        // Save HLS Package and Variants in Database
        return saveHlsPackageAndVariants(asset, masterKey, bucket, variantsToSave, jobId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public VideoHlsPackage saveHlsPackageAndVariants(VideoAsset asset, String masterKey, String bucket,
                                                     List<VideoHlsVariant> variants, Long jobId) {
        VideoHlsPackage hlsPackage = videoHlsPackageRepository.findByVideoAssetId(asset.getId())
                .orElse(VideoHlsPackage.builder()
                        .videoAsset(asset)
                        .variants(new ArrayList<>())
                        .build());

        hlsPackage.setMasterPlaylistKey(masterKey);
        hlsPackage.setStorageBucket(bucket);
        hlsPackage.setStatus(HlsPackageStatus.READY);
        hlsPackage.setVariantCount(variants.size());
        hlsPackage.setTargetDurationSeconds(2);
        hlsPackage.setProcessingJobId(jobId);
        hlsPackage.setCompletedAt(Instant.now());
        hlsPackage.setErrorCode(null);
        hlsPackage.setErrorMessage(null);

        if (hlsPackage.getVariants() == null) {
            hlsPackage.setVariants(new ArrayList<>());
        } else {
            hlsPackage.getVariants().clear();
        }

        for (VideoHlsVariant v : variants) {
            v.setHlsPackage(hlsPackage);
            hlsPackage.getVariants().add(v);
        }

        return videoHlsPackageRepository.save(hlsPackage);
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
    public VideoRendition saveOrUpdateRendition(Long assetId, VideoResolution res, TranscodeProfile profile,
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

        return videoRenditionRepository.save(rendition);
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
