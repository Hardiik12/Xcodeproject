package com.communityott.content.storage;

import com.communityott.common.exception.VideoStorageException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class MinioObjectStorageService implements ObjectStorageService {

    private final MinioClient minioClient;
    private final StorageProperties storageProperties;

    @PostConstruct
    public void init() {
        if (storageProperties.getMinio().isAutoCreateBucket()) {
            ensureBucketExists(storageProperties.getMinio().getBucket());
        }
    }

    private void ensureBucketExists(String bucketName) {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                log.info("Bucket '{}' does not exist. Creating MinIO bucket...", bucketName);
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("Bucket '{}' created successfully.", bucketName);
            }
        } catch (Exception e) {
            log.warn("Could not verify or create MinIO bucket '{}' during startup (MinIO may be offline in test/mock mode): {}", bucketName, e.getMessage());
        }
    }

    @Override
    public String uploadObject(String bucket, String key, InputStream inputStream, long size, String contentType) {
        String targetBucket = (bucket != null && !bucket.isBlank()) ? bucket : storageProperties.getMinio().getBucket();
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(targetBucket)
                            .object(key)
                            .stream(inputStream, size, -1)
                            .contentType(contentType != null ? contentType : "application/octet-stream")
                            .build()
            );
            log.info("Successfully uploaded object to MinIO: bucket={}, key={}, size={} bytes", targetBucket, key, size);
            return key;
        } catch (Exception e) {
            log.error("Failed to upload object to MinIO: bucket={}, key={}. Error: {}", targetBucket, key, e.getMessage(), e);
            throw new VideoStorageException("Failed to upload video asset to storage: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream getObject(String bucket, String key) {
        String targetBucket = (bucket != null && !bucket.isBlank()) ? bucket : storageProperties.getMinio().getBucket();
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(targetBucket)
                            .object(key)
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to get object from MinIO: bucket={}, key={}. Error: {}", targetBucket, key, e.getMessage(), e);
            throw new VideoStorageException("Failed to retrieve object from storage: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean doesObjectExist(String bucket, String key) {
        String targetBucket = (bucket != null && !bucket.isBlank()) ? bucket : storageProperties.getMinio().getBucket();
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(targetBucket)
                            .object(key)
                            .build()
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void deleteObject(String bucket, String key) {
        String targetBucket = (bucket != null && !bucket.isBlank()) ? bucket : storageProperties.getMinio().getBucket();
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(targetBucket)
                            .object(key)
                            .build()
            );
            log.info("Deleted object from MinIO: bucket={}, key={}", targetBucket, key);
        } catch (Exception e) {
            log.error("Failed to delete object from MinIO: bucket={}, key={}. Error: {}", targetBucket, key, e.getMessage(), e);
            throw new VideoStorageException("Failed to delete object from storage: " + e.getMessage(), e);
        }
    }

    @Override
    public String generatePresignedGetUrl(String bucket, String key, Duration duration) {
        String targetBucket = (bucket != null && !bucket.isBlank()) ? bucket : storageProperties.getMinio().getBucket();
        try {
            int expirySeconds = (int) Math.min(duration.getSeconds(), 7 * 24 * 3600);
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(targetBucket)
                            .object(key)
                            .expiry(expirySeconds, TimeUnit.SECONDS)
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to generate presigned GET URL: bucket={}, key={}. Error: {}", targetBucket, key, e.getMessage(), e);
            throw new VideoStorageException("Failed to generate presigned download URL: " + e.getMessage(), e);
        }
    }

    @Override
    public String getBucketName() {
        return storageProperties.getMinio().getBucket();
    }
}
