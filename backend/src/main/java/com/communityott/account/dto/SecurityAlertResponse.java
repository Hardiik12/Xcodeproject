package com.communityott.account.dto;

import com.communityott.account.model.SecurityAlertSeverity;
import com.communityott.account.model.SecurityAlertStatus;
import com.communityott.account.model.SecurityAlertType;
import com.communityott.auth.entity.Platform;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * Privacy-sanitized security alert DTO for end-user consumption.
 * Strictly excludes secrets, tokens, raw IPs, User-Agents, and trace IDs.
 */
@Value
@Builder
public class SecurityAlertResponse {
    Long id;
    SecurityAlertType alertType;
    SecurityAlertSeverity severity;
    String title;
    String message;
    SecurityAlertStatus status;
    Platform platform;
    String maskedIp;
    String approxLocation;
    Instant createdAt;
    Instant readAt;
}
