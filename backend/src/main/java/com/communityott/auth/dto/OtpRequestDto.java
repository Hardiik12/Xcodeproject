package com.communityott.auth.dto;

import com.communityott.auth.entity.AuthIdentifierType;
import com.communityott.auth.entity.OtpPurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request body to generate and dispatch an OTP")
public class OtpRequestDto {

    @NotNull(message = "Identifier type is required (EMAIL or PHONE)")
    @Schema(description = "Type of identifier", example = "EMAIL", requiredMode = Schema.RequiredMode.REQUIRED)
    private AuthIdentifierType identifierType;

    @NotBlank(message = "Identifier is required")
    @Schema(description = "Target email address or phone number", example = "user@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String identifier;

    @NotNull(message = "OTP purpose is required (LOGIN, REGISTRATION, or ACCOUNT_RECOVERY)")
    @Schema(description = "Purpose of the OTP request", example = "LOGIN", requiredMode = Schema.RequiredMode.REQUIRED)
    private OtpPurpose purpose;
}
