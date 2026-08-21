package com.communityott.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Response returned upon successful OTP verification and authentication")
public class AuthenticationResponse {

    @Schema(description = "Indicates if authentication succeeded", example = "true")
    private boolean authenticated;

    @Schema(description = "Short-lived JWT access token for API authorization", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String accessToken;

    @Schema(description = "Cryptographically secure refresh token for token rotation", example = "dGhpcyBpcyBhIHJlYWwgcmVmcmVzaCB0b2tlbg...")
    private String refreshToken;

    @Schema(description = "Type of authorization token", example = "Bearer")
    @Builder.Default
    private String tokenType = "Bearer";

    @Schema(description = "Access token lifetime in seconds", example = "900")
    private Long expiresIn;

    @Schema(description = "Authenticated user profile")
    private AuthenticatedUserResponse user;

    @Schema(description = "Authenticated device session details")
    private AuthSessionResponse session;
}
