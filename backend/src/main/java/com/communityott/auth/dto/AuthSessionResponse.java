package com.communityott.auth.dto;

import com.communityott.auth.entity.Platform;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Authenticated device session details")
public class AuthSessionResponse {

    @Schema(description = "Session unique ID", example = "101")
    private Long id;

    @Schema(description = "Client device identifier", example = "iphone-15-pro-device-id")
    private String deviceId;

    @Schema(description = "Device friendly name", example = "Hardik's iPhone")
    private String deviceName;

    @Schema(description = "Client platform", example = "IOS")
    private Platform platform;

    @Schema(description = "Session creation timestamp")
    private Instant createdAt;

    @Schema(description = "Session expiration timestamp")
    private Instant expiresAt;
}
