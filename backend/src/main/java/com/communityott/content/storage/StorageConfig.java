package com.communityott.content.storage;

import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class StorageConfig {

    private final StorageProperties storageProperties;

    @Bean
    @ConditionalOnMissingBean
    public MinioClient minioClient() {
        StorageProperties.Minio minio = storageProperties.getMinio();
        log.info("Initializing MinIO Client for endpoint: {}", minio.getEndpoint());
        return MinioClient.builder()
                .endpoint(minio.getEndpoint())
                .credentials(minio.getAccessKey(), minio.getSecretKey())
                .build();
    }
}
