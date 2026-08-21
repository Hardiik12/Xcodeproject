package com.communityott.account.controller;

import com.communityott.account.dto.SecurityAlertResponse;
import com.communityott.account.dto.UnreadCountResponse;
import com.communityott.account.model.SecurityAlertStatus;
import com.communityott.account.service.SecurityAlertService;
import com.communityott.common.response.ApiResponse;
import com.communityott.common.security.CommunityOttPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for user-facing security alerts and notification management.
 * All operations resolve user identity strictly from authentication context (Anti-IDOR).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/account/security/alerts")
@RequiredArgsConstructor
@Tag(name = "Account Security Alerts", description = "Endpoints for user security alert notifications and read status management.")
public class AccountSecurityAlertController {

    private final SecurityAlertService securityAlertService;

    @GetMapping
    @Operation(summary = "Get Security Alerts", description = "Retrieves paginated user security alerts for the authenticated account.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Security alerts retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request parameters or page size"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthenticated request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<ApiResponse<Page<SecurityAlertResponse>>> getSecurityAlerts(
            @AuthenticationPrincipal CommunityOttPrincipal principal,
            @Parameter(description = "Filter by status (UNREAD, READ, ARCHIVED)") @RequestParam(required = false) String status,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") Integer page,
            @Parameter(description = "Page size (1-50)") @RequestParam(defaultValue = "20") Integer size
    ) {
        validatePrincipal(principal);
        validatePagination(page, size);

        SecurityAlertStatus alertStatus = null;
        if (status != null && !status.isBlank()) {
            try {
                alertStatus = SecurityAlertStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Invalid alert status filter: " + status);
            }
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<SecurityAlertResponse> alerts = securityAlertService.getUserAlerts(principal.getUserId(), alertStatus, pageable);

        return ResponseEntity.ok(ApiResponse.success(alerts, "Security alerts retrieved successfully"));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get Unread Security Alert Count", description = "Retrieves the count of unread security alerts for the authenticated account.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Unread count retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthenticated request")
    })
    public ResponseEntity<ApiResponse<UnreadCountResponse>> getUnreadCount(
            @AuthenticationPrincipal CommunityOttPrincipal principal
    ) {
        validatePrincipal(principal);
        UnreadCountResponse response = securityAlertService.getUnreadCount(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response, "Unread count retrieved successfully"));
    }

    @PostMapping("/{alertId}/read")
    @Operation(summary = "Mark Alert as Read", description = "Marks a specific user security alert as read.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Alert marked as read"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthenticated request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Security alert not found or access denied")
    })
    public ResponseEntity<ApiResponse<SecurityAlertResponse>> markAlertAsRead(
            @AuthenticationPrincipal CommunityOttPrincipal principal,
            @PathVariable Long alertId
    ) {
        validatePrincipal(principal);
        SecurityAlertResponse response = securityAlertService.markAlertAsRead(alertId, principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response, "Alert marked as read"));
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark All Alerts as Read", description = "Bulk marks all unread security alerts for the authenticated account as read.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "All unread alerts marked as read"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthenticated request")
    })
    public ResponseEntity<ApiResponse<Void>> markAllAlertsAsRead(
            @AuthenticationPrincipal CommunityOttPrincipal principal
    ) {
        validatePrincipal(principal);
        securityAlertService.markAllAlertsAsRead(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success(null, "All unread alerts marked as read"));
    }

    private void validatePrincipal(CommunityOttPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new AccessDeniedException("Authentication required to access account security alerts");
        }
    }

    private void validatePagination(Integer page, Integer size) {
        if (page == null || page < 0) {
            throw new IllegalArgumentException("Page index must be 0 or greater");
        }
        if (size == null || size < 1 || size > 50) {
            throw new IllegalArgumentException("Page size must be between 1 and 50");
        }
    }
}
