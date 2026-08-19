package com.communityott.analytics.controller;

import com.communityott.analytics.dto.AdminSystemAnalyticsResponse;
import com.communityott.analytics.dto.AdminUserAnalyticsResponse;
import com.communityott.analytics.dto.AnalyticsOverviewResponse;
import com.communityott.analytics.dto.AnalyticsTrendResponse;
import com.communityott.analytics.dto.ContentRankingItemDto;
import com.communityott.analytics.dto.PlatformAnalyticsResponse;
import com.communityott.analytics.service.AnalyticsQueryService;
import com.communityott.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin/analytics")
@RequiredArgsConstructor
@Tag(name = "Admin Analytics API", description = "Platform-wide health, operational, system inventory, and viewer analytics for Super Administrators")
@SecurityRequirement(name = "BearerAuth")
@SecurityRequirement(name = "DevUserIdAuth")
public class AdminAnalyticsController {

    private final AnalyticsQueryService queryService;

    @GetMapping("/overview")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'ANALYTICS_VIEW')")
    @Operation(summary = "Get platform-wide analytics overview", description = "Returns aggregated platform metrics for the specified date range. Requires ANALYTICS_VIEW permission.")
    public ApiResponse<AnalyticsOverviewResponse> getOverview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String timeWindow,
            @RequestParam(required = false) String platform) {

        LocalDate effectiveStart = from != null ? from : startDate;
        LocalDate effectiveEnd = to != null ? to : endDate;

        AnalyticsOverviewResponse response = queryService.getOverview(effectiveStart, effectiveEnd, timeWindow, platform);
        return ApiResponse.success(response, "Admin analytics overview retrieved successfully");
    }

    @GetMapping("/content")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'ANALYTICS_VIEW')")
    @Operation(summary = "Get content performance list for admin")
    public ApiResponse<Page<ContentRankingItemDto>> getContentPerformance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String timeWindow,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long languageId,
            @RequestParam(required = false, defaultValue = "WATCH_TIME") String sortBy,
            @RequestParam(required = false, defaultValue = "DESC") String sortDirection,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {

        LocalDate effectiveStart = from != null ? from : startDate;
        LocalDate effectiveEnd = to != null ? to : endDate;

        Page<ContentRankingItemDto> response = queryService.getTopContent(
                effectiveStart, effectiveEnd, timeWindow, platform, categoryId, languageId,
                sortBy, sortDirection, page, size);
        return ApiResponse.success(response, "Admin content analytics retrieved successfully");
    }

    @GetMapping("/trends")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'ANALYTICS_VIEW')")
    @Operation(summary = "Get platform-wide daily trend points")
    public ApiResponse<AnalyticsTrendResponse> getTrends(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String timeWindow) {

        LocalDate effectiveStart = from != null ? from : startDate;
        LocalDate effectiveEnd = to != null ? to : endDate;

        AnalyticsTrendResponse response = queryService.getDailyTrends(effectiveStart, effectiveEnd, timeWindow);
        return ApiResponse.success(response, "Admin daily trends retrieved successfully");
    }

    @GetMapping("/platforms")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'ANALYTICS_VIEW')")
    @Operation(summary = "Get platform distribution analytics for admin")
    public ApiResponse<PlatformAnalyticsResponse> getPlatforms(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String timeWindow) {

        LocalDate effectiveStart = from != null ? from : startDate;
        LocalDate effectiveEnd = to != null ? to : endDate;

        PlatformAnalyticsResponse response = queryService.getPlatformAnalytics(effectiveStart, effectiveEnd, timeWindow);
        return ApiResponse.success(response, "Admin platform analytics retrieved successfully");
    }

    @GetMapping("/system")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'SYSTEM_SETTINGS_VIEW')")
    @Operation(summary = "Get platform system and inventory metrics", description = "Returns operational inventory including total users, content items, video assets, and lifetime streaming metrics. Restricted to Super Admin.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "System analytics retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - requires SYSTEM_SETTINGS_VIEW")
    })
    public ApiResponse<AdminSystemAnalyticsResponse> getSystemAnalytics() {
        AdminSystemAnalyticsResponse response = queryService.getAdminSystemAnalytics();
        return ApiResponse.success(response, "System analytics retrieved successfully");
    }

    @GetMapping("/users")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'SYSTEM_SETTINGS_VIEW')")
    @Operation(summary = "Get aggregate viewer engagement metrics", description = "Returns aggregate active viewer and engagement ratios over a time window without exposing user PII. Restricted to Super Admin.")
    public ApiResponse<AdminUserAnalyticsResponse> getUserAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String timeWindow) {

        LocalDate effectiveStart = from != null ? from : startDate;
        LocalDate effectiveEnd = to != null ? to : endDate;

        AdminUserAnalyticsResponse response = queryService.getAdminUserAnalytics(effectiveStart, effectiveEnd, timeWindow);
        return ApiResponse.success(response, "User aggregate analytics retrieved successfully");
    }
}
