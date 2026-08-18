package com.communityott.analytics.controller;

import com.communityott.analytics.dto.AggregationJobResponse;
import com.communityott.analytics.dto.AnalyticsOverviewResponse;
import com.communityott.analytics.dto.AnalyticsTrendResponse;
import com.communityott.analytics.dto.ContentAnalyticsResponse;
import com.communityott.analytics.dto.ContentRankingItemDto;
import com.communityott.analytics.dto.PlatformAnalyticsResponse;
import com.communityott.analytics.service.AnalyticsAggregationService;
import com.communityott.analytics.service.AnalyticsQueryService;
import com.communityott.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics Aggregation API", description = "Endpoints for viewing aggregate OTT metrics, trends, content performance, and platform distribution")
@SecurityRequirement(name = "BearerAuth")
@SecurityRequirement(name = "DevUserIdAuth")
public class AnalyticsController {

    private final AnalyticsQueryService queryService;
    private final AnalyticsAggregationService aggregationService;

    @GetMapping("/overview")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'ANALYTICS_VIEW')")
    @Operation(summary = "Get platform-wide analytics overview", description = "Returns aggregated high-level metrics for the specified date range. Requires ANALYTICS_VIEW permission.")
    public ApiResponse<AnalyticsOverviewResponse> getOverview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        AnalyticsOverviewResponse response = queryService.getOverview(startDate, endDate);
        return ApiResponse.success(response, "Analytics overview retrieved successfully");
    }

    @GetMapping("/content/{contentId}")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'ANALYTICS_VIEW')")
    @Operation(summary = "Get content-level analytics", description = "Returns aggregated performance metrics for a specific content item. Requires ANALYTICS_VIEW permission.")
    public ApiResponse<ContentAnalyticsResponse> getContentAnalytics(
            @PathVariable Long contentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        ContentAnalyticsResponse response = queryService.getContentAnalytics(contentId, startDate, endDate);
        return ApiResponse.success(response, "Content analytics retrieved successfully");
    }

    @GetMapping("/trends")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'ANALYTICS_VIEW')")
    @Operation(summary = "Get daily trend metrics", description = "Returns daily time-series metrics across the specified date range. Requires ANALYTICS_VIEW permission.")
    public ApiResponse<AnalyticsTrendResponse> getDailyTrends(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        AnalyticsTrendResponse response = queryService.getDailyTrends(startDate, endDate);
        return ApiResponse.success(response, "Daily trends retrieved successfully");
    }

    @GetMapping("/platforms")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'ANALYTICS_VIEW')")
    @Operation(summary = "Get platform distribution analytics", description = "Returns metric distribution across client platforms (IOS, ANDROID, WEB). Requires ANALYTICS_VIEW permission.")
    public ApiResponse<PlatformAnalyticsResponse> getPlatformAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        PlatformAnalyticsResponse response = queryService.getPlatformAnalytics(startDate, endDate);
        return ApiResponse.success(response, "Platform analytics retrieved successfully");
    }

    @GetMapping("/content/top")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'ANALYTICS_VIEW')")
    @Operation(summary = "Get top-ranking content", description = "Returns paginated rankings of top content ordered by WATCH_TIME, VIEWS, or UNIQUE_VIEWERS. Requires ANALYTICS_VIEW permission.")
    public ApiResponse<Page<ContentRankingItemDto>> getTopContent(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false, defaultValue = "WATCH_TIME") String metric,
            @Parameter(description = "Pagination parameters (default page 0, size 20, max size 50)")
            @PageableDefault(size = 20) Pageable pageable) {

        Page<ContentRankingItemDto> response = queryService.getTopContent(startDate, endDate, metric, pageable);
        return ApiResponse.success(response, "Top content ranking retrieved successfully");
    }

    @PostMapping("/aggregate")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'ANALYTICS_VIEW')")
    @Operation(summary = "Trigger incremental aggregation job", description = "Runs the incremental aggregation pipeline to process raw playback events. Requires ANALYTICS_VIEW permission.")
    public ApiResponse<AggregationJobResponse> triggerAggregation(
            @RequestParam(required = false, defaultValue = "500") int batchSize) {

        AggregationJobResponse response = aggregationService.runAggregation(batchSize);
        return ApiResponse.success(response, "Aggregation job executed successfully");
    }
}
