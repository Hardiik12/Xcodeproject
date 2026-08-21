package com.communityott.device.controller;

import com.communityott.audit.publisher.SecurityAuditEventPublisher;
import com.communityott.auth.repository.AuthSessionRepository;
import com.communityott.common.response.ApiResponse;
import com.communityott.common.security.CommunityOttPrincipal;
import com.communityott.device.dto.DeviceResponse;
import com.communityott.device.dto.DeviceRevokeResponse;
import com.communityott.device.service.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
@Tag(name = "Device Management", description = "Endpoints for managing registered client devices, listing active devices, and revoking devices.")
public class DeviceController {

    private final DeviceService deviceService;
    private final AuthSessionRepository authSessionRepository;

    @GetMapping
    @Operation(summary = "List Registered Devices", description = "Retrieves all registered devices belonging to the authenticated user account.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Devices retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthenticated request")
    })
    public ResponseEntity<ApiResponse<List<DeviceResponse>>> getRegisteredDevices(
            @AuthenticationPrincipal CommunityOttPrincipal principal
    ) {
        validatePrincipal(principal);

        Long currentDeviceId = null;
        if (principal.getSessionId() != null) {
            currentDeviceId = authSessionRepository.findById(principal.getSessionId())
                    .map(s -> s.getDeviceEntity() != null ? s.getDeviceEntity().getId() : null)
                    .orElse(null);
        }

        List<DeviceResponse> devices = deviceService.getDevicesForUser(principal.getUserId(), currentDeviceId);
        return ResponseEntity.ok(ApiResponse.success(devices, "Devices retrieved successfully"));
    }

    private final SecurityAuditEventPublisher securityAuditEventPublisher;

    @PostMapping("/{deviceId}/revoke")
    @Operation(summary = "Revoke Registered Device", description = "Revokes a registered device and invalidates all associated active authentication sessions.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Device revoked successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthenticated request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Device not found or does not belong to user")
    })
    public ResponseEntity<ApiResponse<DeviceRevokeResponse>> revokeDevice(
            @PathVariable Long deviceId,
            @AuthenticationPrincipal CommunityOttPrincipal principal
    ) {
        validatePrincipal(principal);

        try {
            DeviceRevokeResponse response = deviceService.revokeDevice(principal.getUserId(), deviceId);
            return ResponseEntity.ok(ApiResponse.success(response, "Device revoked successfully"));
        } catch (com.communityott.common.exception.ApiException ex) {
            if (ex.getStatus() == org.springframework.http.HttpStatus.NOT_FOUND) {
                securityAuditEventPublisher.publish(com.communityott.audit.dto.SecurityAuditEventPayload.builder()
                        .eventType(com.communityott.audit.model.SecurityEventType.SECURITY_IDOR_ATTEMPT)
                        .outcome(com.communityott.audit.model.SecurityEventOutcome.BLOCKED)
                        .reasonCode("CROSS_USER_DEVICE_ACCESS")
                        .userId(principal.getUserId())
                        .deviceId(deviceId)
                        .platform(com.communityott.auth.entity.Platform.WEB)
                        .deviceIdentifier("foreign-device-" + deviceId)
                        .build());
            }
            throw ex;
        }
    }

    private void validatePrincipal(CommunityOttPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new AccessDeniedException("Authentication required to access device management");
        }
    }
}
