package com.communityott.device.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceRevokeResponse {
    private Long deviceId;
    private String status;
    private Instant revokedAt;
    private int sessionsRevokedCount;
}
