package com.communityott.device.service;

import com.communityott.audit.dto.SecurityAuditEventPayload;
import com.communityott.audit.model.SecurityEventOutcome;
import com.communityott.audit.model.SecurityEventType;
import com.communityott.audit.publisher.SecurityAuditEventPublisher;
import com.communityott.auth.dto.OtpVerifyRequestDto;
import com.communityott.auth.entity.AuthSession;
import com.communityott.auth.entity.Platform;
import com.communityott.auth.repository.AuthSessionRepository;
import com.communityott.common.exception.ApiException;
import com.communityott.common.exception.MaxDevicesReachedException;
import com.communityott.device.dto.DeviceResponse;
import com.communityott.device.dto.DeviceRevokeResponse;
import com.communityott.device.entity.Device;
import com.communityott.device.repository.DeviceRepository;
import com.communityott.user.entity.User;
import com.communityott.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private static final int MAX_ACTIVE_DEVICES = 2;

    private final DeviceRepository deviceRepository;
    private final AuthSessionRepository authSessionRepository;
    private final UserRepository userRepository;
    private final SecurityAuditEventPublisher securityAuditEventPublisher;

    /**
     * Transactionally resolves, registers, updates, or reactivates a device registration for an authenticating user.
     * Enforces the 2-active-device limit with pessimistic locking.
     */
    @Transactional
    public Device resolveOrCreateDevice(User user, OtpVerifyRequestDto request) {
        String deviceIdentifier = extractDeviceIdentifier(request);
        Platform platform = request.getPlatform() != null ? request.getPlatform() : Platform.WEB;

        // 1. Acquire pessimistic write lock on User to prevent concurrent registration race conditions
        userRepository.findByIdForUpdate(user.getId())
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));

        // 2. Handle atomic device swap if replaceDeviceId is provided
        if (request.getReplaceDeviceId() != null) {
            revokeDeviceInternal(user.getId(), request.getReplaceDeviceId());
            securityAuditEventPublisher.publish(SecurityAuditEventPayload.builder()
                    .eventType(SecurityEventType.DEVICE_REPLACED)
                    .outcome(SecurityEventOutcome.SUCCESS)
                    .user(user)
                    .userId(user.getId())
                    .deviceIdentifier(deviceIdentifier)
                    .platform(platform)
                    .appVersion(request.getAppVersion())
                    .build());
        }

        // 3. Search for existing device row by (user_id, device_identifier)
        Device device = deviceRepository.findByUserIdAndDeviceIdentifier(user.getId(), deviceIdentifier)
                .orElse(null);

        Instant now = Instant.now();

        if (device != null) {
            if (device.isActive()) {
                // Existing active device -> update last_active_at and safe metadata
                updateDeviceMetadata(device, request, platform, now);
                log.info("Updated active device ID [{}] for user ID [{}]", device.getId(), user.getId());
                return deviceRepository.save(device);
            } else {
                // Reactivation attempt for a previously revoked device
                checkActiveDeviceLimit(user.getId());
                device.setRevokedAt(null);
                updateDeviceMetadata(device, request, platform, now);
                log.info("Reactivated device ID [{}] for user ID [{}]", device.getId(), user.getId());
                Device reactivatedDevice = deviceRepository.save(device);

                securityAuditEventPublisher.publish(SecurityAuditEventPayload.builder()
                        .eventType(SecurityEventType.DEVICE_REACTIVATED)
                        .outcome(SecurityEventOutcome.SUCCESS)
                        .user(user)
                        .userId(user.getId())
                        .device(reactivatedDevice)
                        .deviceId(reactivatedDevice.getId())
                        .deviceIdentifier(deviceIdentifier)
                        .platform(platform)
                        .appVersion(request.getAppVersion())
                        .build());

                return reactivatedDevice;
            }
        }

        // 4. New Device Registration
        checkActiveDeviceLimit(user.getId());

        String displayName = (request.getDeviceName() != null && !request.getDeviceName().isBlank())
                ? request.getDeviceName().trim()
                : (platform + " Device");

        Device newDevice = Device.builder()
                .user(user)
                .deviceIdentifier(deviceIdentifier)
                .platform(platform)
                .deviceModel(request.getDeviceModel())
                .osVersion(request.getOsVersion())
                .appVersion(request.getAppVersion())
                .displayName(displayName)
                .firstRegisteredAt(now)
                .lastActiveAt(now)
                .build();

        Device savedDevice = deviceRepository.save(newDevice);
        log.info("Registered new device ID [{}] identifier [{}] for user ID [{}]",
                savedDevice.getId(), deviceIdentifier, user.getId());

        securityAuditEventPublisher.publish(SecurityAuditEventPayload.builder()
                .eventType(SecurityEventType.DEVICE_REGISTERED)
                .outcome(SecurityEventOutcome.SUCCESS)
                .user(user)
                .userId(user.getId())
                .device(savedDevice)
                .deviceId(savedDevice.getId())
                .deviceIdentifier(deviceIdentifier)
                .platform(platform)
                .appVersion(request.getAppVersion())
                .build());

        return savedDevice;
    }

    /**
     * Lists registered devices for a user.
     */
    @Transactional(readOnly = true)
    public List<DeviceResponse> getDevicesForUser(Long userId, Long currentDeviceId) {
        List<Device> devices = deviceRepository.findAllByUserIdOrderByLastActiveAtDesc(userId);
        return devices.stream()
                .map(d -> mapToDeviceResponse(d, currentDeviceId))
                .toList();
    }

    /**
     * Revokes a device and all its associated active sessions.
     */
    @Transactional
    public DeviceRevokeResponse revokeDevice(Long userId, Long deviceId) {
        if (userId == null || deviceId == null) {
            throw new IllegalArgumentException("User ID and Device ID must not be null");
        }

        Device device = deviceRepository.findByIdAndUserId(deviceId, userId)
                .orElseThrow(() -> new ApiException("Device not found", HttpStatus.NOT_FOUND, "DEVICE_NOT_FOUND"));

        int revokedSessionsCount = revokeDeviceInternal(userId, deviceId);

        return DeviceRevokeResponse.builder()
                .deviceId(device.getId())
                .status("REVOKED")
                .revokedAt(device.getRevokedAt() != null ? device.getRevokedAt() : Instant.now())
                .sessionsRevokedCount(revokedSessionsCount)
                .build();
    }

    private int revokeDeviceInternal(Long userId, Long deviceId) {
        Device device = deviceRepository.findByIdAndUserId(deviceId, userId)
                .orElseThrow(() -> new ApiException("Device to replace not found", HttpStatus.NOT_FOUND, "DEVICE_NOT_FOUND"));

        Instant now = Instant.now();
        if (device.getRevokedAt() == null) {
            device.setRevokedAt(now);
            deviceRepository.save(device);
        }

        List<AuthSession> activeSessions = authSessionRepository.findAllByDeviceEntityIdAndRevokedAtIsNull(deviceId);
        activeSessions.forEach(s -> s.setRevokedAt(now));
        authSessionRepository.saveAll(activeSessions);

        log.info("Revoked device ID [{}] and {} associated sessions for user ID [{}]",
                deviceId, activeSessions.size(), userId);

        securityAuditEventPublisher.publish(SecurityAuditEventPayload.builder()
                .eventType(SecurityEventType.DEVICE_REVOKED)
                .outcome(SecurityEventOutcome.SUCCESS)
                .user(device.getUser())
                .userId(userId)
                .device(device)
                .deviceId(device.getId())
                .deviceIdentifier(device.getDeviceIdentifier())
                .platform(device.getPlatform())
                .build());

        securityAuditEventPublisher.publish(SecurityAuditEventPayload.builder()
                .eventType(SecurityEventType.SESSION_REVOKED)
                .outcome(SecurityEventOutcome.SUCCESS)
                .user(device.getUser())
                .userId(userId)
                .device(device)
                .deviceId(device.getId())
                .deviceIdentifier(device.getDeviceIdentifier())
                .platform(device.getPlatform())
                .reasonCode("DEVICE_REVOKED_CASCADE")
                .build());

        return activeSessions.size();
    }

    private void checkActiveDeviceLimit(Long userId) {
        long activeCount = deviceRepository.countByUserIdAndRevokedAtIsNull(userId);
        if (activeCount >= MAX_ACTIVE_DEVICES) {
            log.warn("Device registration blocked: User ID [{}] has {} active devices (max {})",
                    userId, activeCount, MAX_ACTIVE_DEVICES);

            securityAuditEventPublisher.publish(SecurityAuditEventPayload.builder()
                    .eventType(SecurityEventType.DEVICE_LIMIT_REACHED)
                    .outcome(SecurityEventOutcome.BLOCKED)
                    .reasonCode("MAX_DEVICES_EXCEEDED")
                    .userId(userId)
                    .platform(Platform.WEB)
                    .deviceIdentifier("device-limit-reached")
                    .build());

            List<DeviceResponse> activeDevices = getDevicesForUser(userId, null).stream()
                    .filter(d -> "ACTIVE".equalsIgnoreCase(d.getStatus()))
                    .toList();
            throw new MaxDevicesReachedException(activeDevices);
        }
    }

    private String extractDeviceIdentifier(OtpVerifyRequestDto request) {
        if (request.getDeviceId() == null || request.getDeviceId().isBlank()) {
            throw new ApiException("device_identifier (deviceId) is required", HttpStatus.BAD_REQUEST, "INVALID_DEVICE_IDENTIFIER");
        }
        return request.getDeviceId().trim();
    }

    private void updateDeviceMetadata(Device device, OtpVerifyRequestDto request, Platform platform, Instant now) {
        device.setLastActiveAt(now);
        device.setPlatform(platform);
        if (request.getDeviceName() != null && !request.getDeviceName().isBlank()) {
            device.setDisplayName(request.getDeviceName().trim());
        }
        if (request.getDeviceModel() != null && !request.getDeviceModel().isBlank()) {
            device.setDeviceModel(request.getDeviceModel().trim());
        }
        if (request.getOsVersion() != null && !request.getOsVersion().isBlank()) {
            device.setOsVersion(request.getOsVersion().trim());
        }
        if (request.getAppVersion() != null && !request.getAppVersion().isBlank()) {
            device.setAppVersion(request.getAppVersion().trim());
        }
    }

    private DeviceResponse mapToDeviceResponse(Device device, Long currentDeviceId) {
        return DeviceResponse.builder()
                .id(device.getId())
                .platform(device.getPlatform())
                .deviceModel(device.getDeviceModel())
                .osVersion(device.getOsVersion())
                .appVersion(device.getAppVersion())
                .displayName(device.getDisplayName())
                .firstRegisteredAt(device.getFirstRegisteredAt())
                .lastActiveAt(device.getLastActiveAt())
                .isCurrentDevice(currentDeviceId != null && currentDeviceId.equals(device.getId()))
                .status(device.isActive() ? "ACTIVE" : "REVOKED")
                .build();
    }
}
