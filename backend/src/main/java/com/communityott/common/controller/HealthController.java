package com.communityott.common.controller;

import com.communityott.common.response.ApiResponse;
import com.communityott.common.util.MinioVerificationComponent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
@Tag(name = "Health API", description = "System Foundation Health & Verification Endpoints")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MinioVerificationComponent minioVerificationComponent;

    @GetMapping
    @Operation(summary = "Get Application Health Status", description = "Returns service health and status of PostgreSQL, Redis, and MinIO connections.")
    public ApiResponse<Map<String, Object>> getHealth() {
        Map<String, Object> statusMap = new LinkedHashMap<>();
        statusMap.put("service", "communityott-backend");
        statusMap.put("status", "UP");

        // Database status check
        String dbStatus = checkDatabase();
        statusMap.put("database", dbStatus);

        // Redis status check
        String redisStatus = checkRedis();
        statusMap.put("redis", redisStatus);

        // MinIO status check
        String minioStatus = checkMinio();
        statusMap.put("minio", minioStatus);

        return ApiResponse.success(statusMap, "CommunityOTT backend is running");
    }

    private String checkDatabase() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return (result != null && result == 1) ? "CONNECTED" : "DOWN";
        } catch (Exception e) {
            return "DOWN (" + e.getMessage() + ")";
        }
    }

    private String checkRedis() {
        try {
            String pingResult = redisTemplate.getConnectionFactory().getConnection().ping();
            return "PONG".equalsIgnoreCase(pingResult) ? "CONNECTED" : "DOWN";
        } catch (Exception e) {
            return "DOWN (" + e.getMessage() + ")";
        }
    }

    private String checkMinio() {
        try {
            Health health = minioVerificationComponent.health();
            return "UP".equalsIgnoreCase(health.getStatus().getCode()) ? "CONNECTED" : "DOWN";
        } catch (Exception e) {
            return "DOWN (" + e.getMessage() + ")";
        }
    }
}
