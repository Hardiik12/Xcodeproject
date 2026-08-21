package com.communityott.device.dto;

import com.communityott.auth.entity.Platform;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceResponse {
    private Long id;
    private Platform platform;
    private String deviceModel;
    private String osVersion;
    private String appVersion;
    private String displayName;
    private Instant firstRegisteredAt;
    private Instant lastActiveAt;
    private Boolean isCurrentDevice;
    private String status;
}
