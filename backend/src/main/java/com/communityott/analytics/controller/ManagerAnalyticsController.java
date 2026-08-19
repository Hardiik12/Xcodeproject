package com.communityott.analytics.controller;

import com.communityott.analytics.dto.AnalyticsTrendResponse;
import com.communityott.analytics.dto.CategoryAnalyticsResponse;
import com.communityott.analytics.dto.ContentAnalyticsResponse;
import com.communityott.analytics.dto.ContentRankingItemDto;
import com.communityott.analytics.dto.LanguageAnalyticsResponse;
import com.communityott.analytics.dto.ManagerOverviewResponse;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/manager/analytics")
@RequiredArgsConstructor
@Tag(name = "Manager Analytics API", description = "Operational business and content analytics endpoints for Content Managers")
@SecurityRequirement(name = "BearerAuth")
@SecurityRequirement(name = "DevUserIdAuth")
public class ManagerAnalyticsController {

    private final AnalyticsQueryService queryService;

    @GetMapping("/overview")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'ANALYTICS_VIEW')")
    @Operation(summary = "Get manager dashboard overview with period-over-period comparison", description = "Returns high-level business metrics, period-over-period growth indicators, and top content.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Manager overview retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid date range or platform parameter"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ApiResponse<ManagerOverviewResponse> getOverview(
            @Parameter(description = "Start date (ISO-8601 YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "End date (ISO-8601 YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "Alias for startDate (ISO-8601 YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Alias for endDate (ISO-8601 YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Predefined time window: TODAY, YESTERDAY, LAST_7_DAYS, LAST_30_DAYS, CUSTOM")
            @RequestParam(required = false) String timeWindow,
            @Parameter(description = "Filter by client platform: IOS, ANDROID, WEB")
            @RequestParam(required = false) String platform) {

        LocalDate effectiveStart = from != null ? from : startDate;
        LocalDate effectiveEnd = to != null ? to : endDate;

        ManagerOverviewResponse response = queryService.getManagerOverview(effectiveStart, effectiveEnd, timeWindow, platform);
        return ApiResponse.success(response, "Manager analytics overview retrieved successfully");
    }

    @GetMapping("/content")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'ANALYTICS_VIEW')")
    @Operation(summary = "Get content performance list", description = "Returns paginated content performance rankings with filter and sort capabilities.")
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
        return ApiResponse.success(response, "Content performance list retrieved successfully");
    }

    @GetMapping("/content/{contentId}")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'ANALYTICS_VIEW')")
    @Operation(summary = "Get individual content performance detail")
    public ApiResponse<ContentAnalyticsResponse> getContentDetail(
            @PathVariable Long contentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String timeWindow) {

        LocalDate effectiveStart = from != null ? from : startDate;
        LocalDate effectiveEnd = to != null ? to : endDate;

        ContentAnalyticsResponse response = queryService.getContentAnalytics(contentId, effectiveStart, effectiveEnd, timeWindow);
        return ApiResponse.success(response, "Content detail analytics retrieved successfully");
    }

    @GetMapping("/top-content")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'ANALYTICS_VIEW')")
    @Operation(summary = "Get top content rankings")
    public ApiResponse<Page<ContentRankingItemDto>> getTopContent(
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
        return ApiResponse.success(response, "Top content retrieved successfully");
    }

    @GetMapping("/trends")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'ANALYTICS_VIEW')")
    @Operation(summary = "Get daily trend points")
    public ApiResponse<AnalyticsTrendResponse> getTrends(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String timeWindow) {

        LocalDate effectiveStart = from != null ? from : startDate;
        LocalDate effectiveEnd = to != null ? to : endDate;

        AnalyticsTrendResponse response = queryService.getDailyTrends(effectiveStart, effectiveEnd, timeWindow);
        return ApiResponse.success(response, "Daily trends retrieved successfully");
    }

    @GetMapping("/categories")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'ANALYTICS_VIEW')")
    @Operation(summary = "Get category performance breakdown")
    public ApiResponse<CategoryAnalyticsResponse> getCategoryAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String timeWindow) {

        LocalDate effectiveStart = from != null ? from : startDate;
        LocalDate effectiveEnd = to != null ? to : endDate;

        CategoryAnalyticsResponse response = queryService.getCategoryAnalytics(effectiveStart, effectiveEnd, timeWindow);
        return ApiResponse.success(response, "Category analytics retrieved successfully");
    }

    @GetMapping("/languages")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'ANALYTICS_VIEW')")
    @Operation(summary = "Get language performance breakdown")
    public ApiResponse<LanguageAnalyticsResponse> getLanguageAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String timeWindow) {

        LocalDate effectiveStart = from != null ? from : startDate;
        LocalDate effectiveEnd = to != null ? to : endDate;

        LanguageAnalyticsResponse response = queryService.getLanguageAnalytics(effectiveStart, effectiveEnd, timeWindow);
        return ApiResponse.success(response, "Language analytics retrieved successfully");
    }

    @GetMapping("/platforms")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'ANALYTICS_VIEW')")
    @Operation(summary = "Get platform distribution analytics")
    public ApiResponse<PlatformAnalyticsResponse> getPlatforms(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String timeWindow) {

        LocalDate effectiveStart = from != null ? from : startDate;
        LocalDate effectiveEnd = to != null ? to : endDate;

        PlatformAnalyticsResponse response = queryService.getPlatformAnalytics(effectiveStart, effectiveEnd, timeWindow);
        return ApiResponse.success(response, "Platform analytics retrieved successfully");
    }
}
