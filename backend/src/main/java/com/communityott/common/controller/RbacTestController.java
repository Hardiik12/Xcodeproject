package com.communityott.common.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Temporary verification controller for Phase 3.5 Spring Security + RBAC Authorization testing.
 *
 * <p><strong>NOTICE:</strong> These endpoints exist strictly for verifying permission-based authorization
 * with Spring Security method expressions {@code @PreAuthorize}. They will be replaced or removed in future phases.</p>
 */
@Tag(name = "RBAC Test Verification (Phase 3.5 Temporary)", description = "Temporary test endpoints to verify Spring Security permission-based authorization")
@RestController
@RequestMapping("/api/v1/rbac/test")
public class RbacTestController {

    @Operation(summary = "Test USER_VIEW permission authorization")
    @GetMapping("/user-view")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'USER_VIEW')")
    public ResponseEntity<Map<String, Object>> testUserViewPermission() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "endpoint", "/api/v1/rbac/test/user-view",
                "requiredPermission", "USER_VIEW",
                "message", "Access granted to USER_VIEW protected endpoint"
        ));
    }

    @Operation(summary = "Test VIDEO_UPLOAD permission authorization")
    @GetMapping("/video-upload")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'VIDEO_UPLOAD')")
    public ResponseEntity<Map<String, Object>> testVideoUploadPermission() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "endpoint", "/api/v1/rbac/test/video-upload",
                "requiredPermission", "VIDEO_UPLOAD",
                "message", "Access granted to VIDEO_UPLOAD protected endpoint"
        ));
    }

    @Operation(summary = "Test ANALYTICS_VIEW permission authorization")
    @GetMapping("/analytics")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'ANALYTICS_VIEW')")
    public ResponseEntity<Map<String, Object>> testAnalyticsViewPermission() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "endpoint", "/api/v1/rbac/test/analytics",
                "requiredPermission", "ANALYTICS_VIEW",
                "message", "Access granted to ANALYTICS_VIEW protected endpoint"
        ));
    }

    @Operation(summary = "Test ROLE_CREATE permission authorization")
    @GetMapping("/admin")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'ROLE_CREATE')")
    public ResponseEntity<Map<String, Object>> testAdminPermission() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "endpoint", "/api/v1/rbac/test/admin",
                "requiredPermission", "ROLE_CREATE",
                "message", "Access granted to ROLE_CREATE protected endpoint"
        ));
    }

    @Operation(summary = "Test VIDEO_PUBLISH permission authorization")
    @GetMapping("/video-publish")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'VIDEO_PUBLISH')")
    public ResponseEntity<Map<String, Object>> testVideoPublishPermission() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "endpoint", "/api/v1/rbac/test/video-publish",
                "requiredPermission", "VIDEO_PUBLISH",
                "message", "Access granted to VIDEO_PUBLISH protected endpoint"
        ));
    }
}
