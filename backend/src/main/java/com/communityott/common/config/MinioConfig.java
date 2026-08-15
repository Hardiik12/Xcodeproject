package com.communityott.common.config;

import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class MinioConfig {

    @Value("${communityott.minio.endpoint:http://localhost:9000}")
    private String endpoint;

    @Value("${communityott.minio.access-key:communityott}")
    private String accessKey;

    @Value("${communityott.minio.secret-key:communityott_minio_password}")
    private String secretKey;

    @Bean
    public MinioClient minioClient() {
        log.info("Initializing MinIO Client for local S3 storage at endpoint: {}", endpoint);
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
