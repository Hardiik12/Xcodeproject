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
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
@Tag(name = "Analytics API", description = "Endpoints for platform metrics, content performance, trends, platform distribution, and rankings")
@SecurityRequirement(name = "BearerAuth")
@SecurityRequirement(name = "DevUserIdAuth")
public class AnalyticsController {

    private final AnalyticsQueryService queryService;
    private final AnalyticsAggregationService aggregationService;

    @GetMapping("/overview")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'ANALYTICS_VIEW')")
    @Operation(summary = "Get platform analytics overview", description = "Returns aggregated platform metrics for the specified date range or time window. Requires ANALYTICS_VIEW permission.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Analytics overview retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid date range or platform parameter"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized - authentication required"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions")
    })
    public ApiResponse<AnalyticsOverviewResponse> getOverview(
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

        AnalyticsOverviewResponse response = queryService.getOverview(effectiveStart, effectiveEnd, timeWindow, platform);
        return ApiResponse.success(response, "Analytics overview retrieved successfully");
    }

    @GetMapping("/content/{contentId}")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'ANALYTICS_VIEW')")
    @Operation(summary = "Get content-level analytics", description = "Returns aggregated performance metrics for a specific content item. Requires ANALYTICS_VIEW permission.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Content analytics retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid date range parameter"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized - authentication required"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Content not found")
    })
    public ApiResponse<ContentAnalyticsResponse> getContentAnalytics(
            @Parameter(description = "Content ID", required = true)
            @PathVariable Long contentId,
            @Parameter(description = "Start date (ISO-8601 YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "End date (ISO-8601 YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "Alias for startDate (ISO-8601 YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Alias for endDate (ISO-8601 YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Predefined time window: TODAY, YESTERDAY, LAST_7_DAYS, LAST_30_DAYS, CUSTOM")
            @RequestParam(required = false) String timeWindow) {

        LocalDate effectiveStart = from != null ? from : startDate;
        LocalDate effectiveEnd = to != null ? to : endDate;

        ContentAnalyticsResponse response = queryService.getContentAnalytics(contentId, effectiveStart, effectiveEnd, timeWindow);
        return ApiResponse.success(response, "Content analytics retrieved successfully");
    }

    @GetMapping("/trends")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'ANALYTICS_VIEW')")
    @Operation(summary = "Get daily trend metrics", description = "Returns continuous daily time-series metrics across the specified date range. Requires ANALYTICS_VIEW permission.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Daily trends retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid date range parameter"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized - authentication required"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions")
    })
    public ApiResponse<AnalyticsTrendResponse> getDailyTrends(
            @Parameter(description = "Start date (ISO-8601 YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "End date (ISO-8601 YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "Alias for startDate (ISO-8601 YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Alias for endDate (ISO-8601 YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Predefined time window: TODAY, YESTERDAY, LAST_7_DAYS, LAST_30_DAYS, CUSTOM")
            @RequestParam(required = false) String timeWindow) {

        LocalDate effectiveStart = from != null ? from : startDate;
        LocalDate effectiveEnd = to != null ? to : endDate;

        AnalyticsTrendResponse response = queryService.getDailyTrends(effectiveStart, effectiveEnd, timeWindow);
        return ApiResponse.success(response, "Daily trends retrieved successfully");
    }

    @GetMapping("/platforms")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'ANALYTICS_VIEW')")
    @Operation(summary = "Get platform distribution analytics", description = "Returns metric distribution across all client platforms (IOS, ANDROID, WEB). Requires ANALYTICS_VIEW permission.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Platform analytics retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid date range parameter"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized - authentication required"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions")
    })
    public ApiResponse<PlatformAnalyticsResponse> getPlatformAnalytics(
            @Parameter(description = "Start date (ISO-8601 YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "End date (ISO-8601 YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "Alias for startDate (ISO-8601 YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Alias for endDate (ISO-8601 YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Predefined time window: TODAY, YESTERDAY, LAST_7_DAYS, LAST_30_DAYS, CUSTOM")
            @RequestParam(required = false) String timeWindow) {

        LocalDate effectiveStart = from != null ? from : startDate;
        LocalDate effectiveEnd = to != null ? to : endDate;

        PlatformAnalyticsResponse response = queryService.getPlatformAnalytics(effectiveStart, effectiveEnd, timeWindow);
        return ApiResponse.success(response, "Platform analytics retrieved successfully");
    }

    @GetMapping("/content/top")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'ANALYTICS_VIEW')")
    @Operation(summary = "Get top-ranking content", description = "Returns paginated rankings of top content ordered by WATCH_TIME, VIEWS, UNIQUE_VIEWERS, or COMPLETIONS. Requires ANALYTICS_VIEW permission.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Top content ranking retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid date range, sort, platform, or pagination parameters"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized - authentication required"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions")
    })
    public ApiResponse<Page<ContentRankingItemDto>> getTopContent(
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
            @RequestParam(required = false) String platform,
            @Parameter(description = "Filter by category ID")
            @RequestParam(required = false) Long categoryId,
            @Parameter(description = "Filter by language ID")
            @RequestParam(required = false) Long languageId,
            @Parameter(description = "Sort metric: WATCH_TIME (default), VIEWS, UNIQUE_VIEWERS, COMPLETIONS")
            @RequestParam(required = false, defaultValue = "WATCH_TIME") String sortBy,
            @Parameter(description = "Sort direction: DESC (default), ASC")
            @RequestParam(required = false, defaultValue = "DESC") String sortDirection,
            @Parameter(description = "Zero-indexed page number (min: 0)")
            @RequestParam(required = false, defaultValue = "0") int page,
            @Parameter(description = "Page size (min: 1, max: 100, default: 20)")
            @RequestParam(required = false, defaultValue = "20") int size) {

        LocalDate effectiveStart = from != null ? from : startDate;
        LocalDate effectiveEnd = to != null ? to : endDate;

        Page<ContentRankingItemDto> response = queryService.getTopContent(
                effectiveStart, effectiveEnd, timeWindow, platform, categoryId, languageId,
                sortBy, sortDirection, page, size);

        return ApiResponse.success(response, "Top content ranking retrieved successfully");
    }

    @PostMapping("/aggregate")
    @PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'ANALYTICS_VIEW')")
    @Operation(summary = "Trigger incremental aggregation job", description = "Runs the incremental aggregation pipeline to process raw playback events. Requires ANALYTICS_VIEW permission.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Aggregation job executed successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized - authentication required"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions")
    })
    public ApiResponse<AggregationJobResponse> triggerAggregation(
            @Parameter(description = "Maximum batch size of raw events to process")
            @RequestParam(required = false, defaultValue = "500") int batchSize) {

        AggregationJobResponse response = aggregationService.runAggregation(batchSize);
        return ApiResponse.success(response, "Aggregation job executed successfully");
    }
}
