package com.communityott.auth.controller;

import com.communityott.auth.dto.AuthenticationResponse;
import com.communityott.auth.dto.OtpRequestDto;
import com.communityott.auth.dto.OtpRequestResult;
import com.communityott.auth.dto.OtpVerifyRequestDto;
import com.communityott.auth.service.AuthenticationService;
import com.communityott.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints for OTP generation, delivery, verification, and session authentication")
public class AuthController {

    private final AuthenticationService authenticationService;

    @PostMapping("/otp/request")
    @Operation(summary = "Request OTP", description = "Generates and dispatches a 6-digit OTP to the user's email or phone for LOGIN, REGISTRATION, or ACCOUNT_RECOVERY.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OTP generated and dispatched successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request format or invalid identifier"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Account is suspended or deleted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Account not found for LOGIN purpose"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Account already exists for REGISTRATION purpose"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit or cooldown exceeded"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "OTP delivery provider failed")
    })
    public ResponseEntity<ApiResponse<OtpRequestResult>> requestOtp(@Valid @RequestBody OtpRequestDto request) {
        OtpRequestResult result = authenticationService.requestOtp(request);
        return ResponseEntity.ok(ApiResponse.success(result, "OTP sent successfully"));
    }

    @PostMapping("/otp/verify")
    @Operation(summary = "Verify OTP & Authenticate", description = "Verifies the submitted 6-digit OTP code, registers/resolves the user, and creates an authenticated AuthSession.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Authentication successful"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid or expired OTP code"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Account is suspended or deleted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Account not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Account already exists"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Maximum verification attempts exceeded")
    })
    public ResponseEntity<ApiResponse<AuthenticationResponse>> verifyOtp(
            @Valid @RequestBody OtpVerifyRequestDto request,
            HttpServletRequest httpRequest
    ) {
        String ipAddress = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");

        AuthenticationResponse response = authenticationService.verifyOtpAndAuthenticate(request, ipAddress, userAgent);
        return ResponseEntity.ok(ApiResponse.success(response, "Authentication successful"));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh Authentication Tokens", description = "Exchanges a valid active refresh token for a new short-lived JWT access token and rotated refresh token.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Token refreshed successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Missing or invalid refresh token request format"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid, expired, revoked, or reused refresh token")
    })
    public ResponseEntity<ApiResponse<AuthenticationResponse>> refresh(
            @Valid @RequestBody com.communityott.auth.dto.RefreshTokenRequestDto request,
            HttpServletRequest httpRequest
    ) {
        String ipAddress = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");

        AuthenticationResponse response = authenticationService.refreshTokens(request, ipAddress, userAgent);
        return ResponseEntity.ok(ApiResponse.success(response, "Token refreshed successfully"));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout & Revoke Current Session", description = "Revokes the active device session and invalidates the associated refresh token state.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Logged out successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthenticated request")
    })
    public ResponseEntity<ApiResponse<Void>> logout(
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.communityott.common.security.CommunityOttPrincipal principal
    ) {
        if (principal == null || principal.getUserId() == null) {
            throw new org.springframework.security.authentication.BadCredentialsException("Unauthenticated request");
        }
        authenticationService.logout(principal.getUserId(), principal.getSessionId());
        return ResponseEntity.ok(ApiResponse.success(null, "Logout successful"));
    }

    @PostMapping("/logout-all")
    @Operation(summary = "Logout All Active Sessions", description = "Revokes all active device sessions belonging to the authenticated user account.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "All active sessions logged out successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthenticated request")
    })
    public ResponseEntity<ApiResponse<Void>> logoutAll(
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.communityott.common.security.CommunityOttPrincipal principal
    ) {
        if (principal == null || principal.getUserId() == null) {
            throw new org.springframework.security.authentication.BadCredentialsException("Unauthenticated request");
        }
        authenticationService.logoutAll(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success(null, "All active sessions logged out successfully"));
    }
}
