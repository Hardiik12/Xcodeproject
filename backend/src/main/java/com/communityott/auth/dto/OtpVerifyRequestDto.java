package com.communityott.auth.dto;

import com.communityott.auth.entity.AuthIdentifierType;
import com.communityott.auth.entity.OtpPurpose;
import com.communityott.auth.entity.Platform;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request body to verify an OTP and establish an authenticated session")
public class OtpVerifyRequestDto {

    @NotNull(message = "Identifier type is required (EMAIL or PHONE)")
    @Schema(description = "Type of identifier", example = "EMAIL", requiredMode = Schema.RequiredMode.REQUIRED)
    private AuthIdentifierType identifierType;

    @NotBlank(message = "Identifier is required")
    @Schema(description = "Target email address or phone number", example = "user@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String identifier;

    @NotBlank(message = "OTP code is required")
    @Pattern(regexp = "^\\d{6}$", message = "OTP must be a 6-digit numeric code")
    @Schema(description = "6-digit OTP code received by user", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String otp;

    @NotNull(message = "OTP purpose is required (LOGIN, REGISTRATION, or ACCOUNT_RECOVERY)")
    @Schema(description = "Purpose of the OTP request", example = "LOGIN", requiredMode = Schema.RequiredMode.REQUIRED)
    private OtpPurpose purpose;

    @Schema(description = "Unique client device identifier", example = "iphone-15-pro-device-id")
    private String deviceId;

    @Schema(description = "Human readable device name", example = "Hardik's iPhone")
    private String deviceName;

    @Schema(description = "Client platform (IOS, ANDROID, or WEB)", example = "IOS")
    private Platform platform;
}
