package com.communityott.account.dto;

import com.communityott.auth.entity.Platform;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginHistoryItemResponse {

    private Long id;

    private String event;

    private String status;

    @JsonProperty("device_name")
    private String deviceName;

    private Platform platform;

    @JsonProperty("os_version")
    private String osVersion;

    @JsonProperty("app_version")
    private String appVersion;

    @JsonProperty("masked_ip")
    private String maskedIp;

    @JsonProperty("approx_location")
    private String approxLocation;

    @JsonProperty("is_current_device")
    private Boolean isCurrentDevice;

    @JsonProperty("user_message")
    private String userMessage;

    @JsonProperty("occurred_at")
    private Instant occurredAt;
}
