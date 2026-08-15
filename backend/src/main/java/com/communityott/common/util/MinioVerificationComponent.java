package com.communityott.common.util;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MinioVerificationComponent implements HealthIndicator {

    private final MinioClient minioClient;

    @Value("${communityott.minio.bucket:communityott-media}")
    private String bucketName;

    @Override
    public Health health() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build()
            );
            if (exists) {
                return Health.up()
                        .withDetail("bucket", bucketName)
                        .withDetail("status", "AVAILABLE")
                        .build();
            } else {
                return Health.down()
                        .withDetail("bucket", bucketName)
                        .withDetail("status", "BUCKET_NOT_FOUND")
                        .build();
            }
        } catch (Exception e) {
            log.error("MinIO health check failed: {}", e.getMessage());
            return Health.down(e)
                    .withDetail("bucket", bucketName)
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
