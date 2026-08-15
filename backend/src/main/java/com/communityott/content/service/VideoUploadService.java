package com.communityott.content.service;

import com.communityott.common.exception.ContentNotFoundException;
import com.communityott.common.exception.InvalidContentStateTransitionException;
import com.communityott.common.exception.VideoAssetNotFoundException;
import com.communityott.common.exception.VideoStorageException;
import com.communityott.content.dto.VideoAssetResponse;
import com.communityott.content.entity.Content;
import com.communityott.content.entity.ContentStatus;
import com.communityott.content.entity.VideoAsset;
import com.communityott.content.entity.VideoAssetStatus;
import com.communityott.content.repository.ContentRepository;
import com.communityott.content.repository.VideoAssetRepository;
import com.communityott.content.storage.ChecksumUtility;
import com.communityott.content.storage.ObjectStorageService;
import com.communityott.content.storage.StorageKeyGenerator;
import com.communityott.content.storage.VideoUploadValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class VideoUploadService {

    private final ContentRepository contentRepository;
    private final VideoAssetRepository videoAssetRepository;
    private final ObjectStorageService objectStorageService;
    private final VideoUploadValidator videoUploadValidator;
    private final StorageKeyGenerator storageKeyGenerator;
    private final VideoProcessingService videoProcessingService;

    @Transactional
    public VideoAssetResponse uploadVideo(Long contentId, MultipartFile file, Long userId) {
        log.info("Processing video upload request for contentId={}, filename={}, size={} bytes, userId={}",
                contentId, file.getOriginalFilename(), file.getSize(), userId);

        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new ContentNotFoundException(contentId));

        if (content.getStatus() == ContentStatus.ARCHIVED) {
            throw new InvalidContentStateTransitionException("Cannot upload video to an ARCHIVED content item");
        }

        // Validate MIME type, extension, size
        videoUploadValidator.validate(file);

        String checksumSha256;
        try (InputStream is = new BufferedInputStream(file.getInputStream())) {
            checksumSha256 = ChecksumUtility.calculateSha256(is);
        } catch (Exception e) {
            log.error("Failed to compute SHA-256 checksum during upload for contentId={}: {}", contentId, e.getMessage(), e);
            throw new VideoStorageException("Failed to process video file checksum: " + e.getMessage(), e);
        }

        String storageBucket = objectStorageService.getBucketName();
        String storageKey = storageKeyGenerator.generateSourceKey(contentId, checksumSha256, file.getOriginalFilename());

        // Upload stream to MinIO Object Storage
        try (InputStream uploadStream = new BufferedInputStream(file.getInputStream())) {
            objectStorageService.uploadObject(
                    storageBucket,
                    storageKey,
                    uploadStream,
                    file.getSize(),
                    file.getContentType()
            );
        } catch (Exception e) {
            log.error("Failed to store video in object storage for contentId={}, storageKey={}: {}", contentId, storageKey, e.getMessage(), e);
            throw new VideoStorageException("Failed to store video file in object storage: " + e.getMessage(), e);
        }

        // Persist VideoAsset domain model
        VideoAsset videoAsset = VideoAsset.builder()
                .content(content)
                .originalFilename(file.getOriginalFilename())
                .fileSizeBytes(file.getSize())
                .contentType(file.getContentType())
                .checksumSha256(checksumSha256)
                .storageBucket(storageBucket)
                .storageKey(storageKey)
                .status(VideoAssetStatus.UPLOADED)
                .createdBy(userId)
                .updatedBy(userId)
                .build();

        VideoAsset savedAsset = videoAssetRepository.save(videoAsset);

        // Update content status to UPLOADING if in DRAFT or FAILED
        if (content.getStatus() == ContentStatus.DRAFT || content.getStatus() == ContentStatus.FAILED) {
            content.setStatus(ContentStatus.UPLOADING);
            content.setUpdatedBy(userId);
            contentRepository.save(content);
            log.info("Transitioned contentId={} status to UPLOADING following video upload", contentId);
        }

        // Automatically enqueue probe processing job
        try {
            videoProcessingService.createAndEnqueueProbeJob(savedAsset.getId(), userId);
        } catch (Exception e) {
            log.warn("Failed to automatically enqueue probe job for asset ID {}: {}", savedAsset.getId(), e.getMessage());
        }

        log.info("Successfully registered video asset id={} for contentId={}, key={}", savedAsset.getId(), contentId, storageKey);
        return VideoAssetResponse.fromEntity(savedAsset);
    }

    @Transactional(readOnly = true)
    public List<VideoAssetResponse> getVideoAssetsForContent(Long contentId) {
        if (!contentRepository.existsById(contentId)) {
            throw new ContentNotFoundException(contentId);
        }
        return videoAssetRepository.findByContentIdOrderByCreatedAtDesc(contentId)
                .stream()
                .map(VideoAssetResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public VideoAssetResponse getVideoAssetById(Long contentId, Long videoId) {
        VideoAsset asset = videoAssetRepository.findByIdAndContentId(videoId, contentId)
                .orElseThrow(() -> new VideoAssetNotFoundException(videoId));
        return VideoAssetResponse.fromEntity(asset);
    }

    @Transactional
    public void deleteVideoAsset(Long contentId, Long videoId, Long userId) {
        VideoAsset asset = videoAssetRepository.findByIdAndContentId(videoId, contentId)
                .orElseThrow(() -> new VideoAssetNotFoundException(videoId));

        try {
            objectStorageService.deleteObject(asset.getStorageBucket(), asset.getStorageKey());
        } catch (Exception e) {
            log.warn("Failed to delete object from storage during asset deletion (key={}): {}", asset.getStorageKey(), e.getMessage());
        }

        asset.setStatus(VideoAssetStatus.DELETED);
        asset.setUpdatedBy(userId);
        videoAssetRepository.save(asset);
        log.info("Marked video asset id={} as DELETED for contentId={}", videoId, contentId);
    }
}
