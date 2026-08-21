package com.communityott.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request body for refreshing access and refresh tokens")
public class RefreshTokenRequestDto {

    @NotBlank(message = "Refresh token must not be blank")
    @Schema(description = "Valid refresh token string", example = "dGhpcyBpcyBhIHJlYWwgcmVmcmVzaCB0b2tlbg...")
    private String refreshToken;
}
