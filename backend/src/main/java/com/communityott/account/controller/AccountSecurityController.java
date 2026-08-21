package com.communityott.account.controller;

import com.communityott.account.dto.LoginHistoryResponse;
import com.communityott.account.service.AccountSecurityService;
import com.communityott.auth.entity.Platform;
import com.communityott.common.response.ApiResponse;
import com.communityott.common.security.CommunityOttPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@Slf4j
@RestController
@RequestMapping("/api/v1/account")
@RequiredArgsConstructor
@Tag(name = "Account Security", description = "Endpoints for user-facing login activity history and security status.")
public class AccountSecurityController {

    private final AccountSecurityService accountSecurityService;

    @GetMapping("/login-history")
    @Operation(summary = "Get User Login History", description = "Retrieves a privacy-filtered, paginated history of security and login activity for the authenticated user.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Login history retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request parameters or date range"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthenticated request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<ApiResponse<LoginHistoryResponse>> getLoginHistory(
            @AuthenticationPrincipal CommunityOttPrincipal principal,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") Integer page,
            @Parameter(description = "Page size (1-50)") @RequestParam(defaultValue = "20") Integer size,
            @Parameter(description = "Start timestamp (ISO-8601)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "End timestamp (ISO-8601)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @Parameter(description = "Filter by event type") @RequestParam(required = false) String event,
            @Parameter(description = "Filter by platform") @RequestParam(required = false) Platform platform
    ) {
        validatePrincipal(principal);

        LoginHistoryResponse response = accountSecurityService.getLoginHistory(
                principal.getUserId(),
                page,
                size,
                from,
                to,
                event,
                platform,
                principal.getSessionId()
        );

        return ResponseEntity.ok(ApiResponse.success(response, "Login history retrieved successfully"));
    }

    private void validatePrincipal(CommunityOttPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new AccessDeniedException("Authentication required to access account login history");
        }
    }
}
