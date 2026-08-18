package com.communityott.analytics.service;

import com.communityott.analytics.dto.AnalyticsOverviewResponse;
import com.communityott.analytics.dto.AnalyticsTrendResponse;
import com.communityott.analytics.dto.ContentAnalyticsResponse;
import com.communityott.analytics.dto.ContentRankingItemDto;
import com.communityott.analytics.dto.DailyTrendPointDto;
import com.communityott.analytics.dto.PlatformAnalyticsResponse;
import com.communityott.analytics.dto.PlatformMetricDto;
import com.communityott.analytics.entity.AnalyticsDailyMetric;
import com.communityott.analytics.repository.AnalyticsDailyMetricRepository;
import com.communityott.auth.entity.Platform;
import com.communityott.common.exception.ContentNotFoundException;
import com.communityott.common.exception.InvalidDateRangeException;
import com.communityott.content.entity.Content;
import com.communityott.content.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsQueryService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(60);
    private static final int MAX_RANGE_DAYS = 90;

    private final AnalyticsDailyMetricRepository dailyMetricRepository;
    private final ContentRepository contentRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    public AnalyticsOverviewResponse getOverview(LocalDate startDate, LocalDate endDate) {
        DateRange range = resolveDateRange(startDate, endDate);
        String cacheKey = "communityott:analytics:overview:" + range.start() + ":" + range.end();

        AnalyticsOverviewResponse cached = getFromCache(cacheKey, AnalyticsOverviewResponse.class);
        if (cached != null) {
            return cached;
        }

        List<AnalyticsDailyMetric> metrics = dailyMetricRepository.findByMetricDateBetween(range.start(), range.end());

        long totalViews = 0;
        long totalPlays = 0;
        long uniqueViewers = 0;
        long totalWatchTime = 0;
        long completedPlays = 0;
        long bufferEvents = 0;
        long errors = 0;
        long qualityChanges = 0;

        for (AnalyticsDailyMetric m : metrics) {
            totalViews += m.getTotalSessions();
            totalPlays += m.getTotalPlays();
            uniqueViewers += m.getUniqueViewers();
            totalWatchTime += m.getTotalWatchTimeSeconds();
            completedPlays += m.getCompletionCount();
            bufferEvents += m.getBufferEventCount();
            errors += m.getErrorCount();
            qualityChanges += m.getQualityChangeCount();
        }

        long avgSessionDuration = totalViews > 0 ? totalWatchTime / totalViews : 0;
        double completionRate = totalViews > 0 ? (double) completedPlays / totalViews : 0.0;

        AnalyticsOverviewResponse response = AnalyticsOverviewResponse.builder()
                .startDate(range.start())
                .endDate(range.end())
                .totalViews(totalViews)
                .totalPlays(totalPlays)
                .uniqueViewers(uniqueViewers)
                .totalWatchTimeSeconds(totalWatchTime)
                .averageSessionDurationSeconds(avgSessionDuration)
                .completedPlays(completedPlays)
                .completionRate(Math.round(completionRate * 100.0) / 100.0)
                .bufferEvents(bufferEvents)
                .playbackErrors(errors)
                .qualityChanges(qualityChanges)
                .build();

        putInCache(cacheKey, response);
        return response;
    }

    public ContentAnalyticsResponse getContentAnalytics(Long contentId, LocalDate startDate, LocalDate endDate) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new ContentNotFoundException(contentId));

        DateRange range = resolveDateRange(startDate, endDate);
        String cacheKey = "communityott:analytics:content:" + contentId + ":" + range.start() + ":" + range.end();

        ContentAnalyticsResponse cached = getFromCache(cacheKey, ContentAnalyticsResponse.class);
        if (cached != null) {
            return cached;
        }

        List<AnalyticsDailyMetric> metrics = dailyMetricRepository.findByContentIdAndMetricDateBetween(
                contentId, range.start(), range.end());

        long totalViews = 0;
        long totalPlays = 0;
        long uniqueViewers = 0;
        long totalWatchTime = 0;
        long completedPlays = 0;
        long bufferEvents = 0;
        long errors = 0;
        long qualityChanges = 0;

        for (AnalyticsDailyMetric m : metrics) {
            totalViews += m.getTotalSessions();
            totalPlays += m.getTotalPlays();
            uniqueViewers += m.getUniqueViewers();
            totalWatchTime += m.getTotalWatchTimeSeconds();
            completedPlays += m.getCompletionCount();
            bufferEvents += m.getBufferEventCount();
            errors += m.getErrorCount();
            qualityChanges += m.getQualityChangeCount();
        }

        long avgSessionDuration = totalViews > 0 ? totalWatchTime / totalViews : 0;
        double completionRate = totalViews > 0 ? (double) completedPlays / totalViews : 0.0;

        ContentAnalyticsResponse response = ContentAnalyticsResponse.builder()
                .contentId(content.getId())
                .title(content.getTitle())
                .thumbnailUrl(content.getThumbnailUrl())
                .startDate(range.start())
                .endDate(range.end())
                .totalViews(totalViews)
                .totalPlays(totalPlays)
                .uniqueViewers(uniqueViewers)
                .totalWatchTimeSeconds(totalWatchTime)
                .averageSessionDurationSeconds(avgSessionDuration)
                .completedPlays(completedPlays)
                .completionRate(Math.round(completionRate * 100.0) / 100.0)
                .bufferEvents(bufferEvents)
                .playbackErrors(errors)
                .qualityChanges(qualityChanges)
                .build();

        putInCache(cacheKey, response);
        return response;
    }

    public AnalyticsTrendResponse getDailyTrends(LocalDate startDate, LocalDate endDate) {
        DateRange range = resolveDateRange(startDate, endDate);
        String cacheKey = "communityott:analytics:trends:" + range.start() + ":" + range.end();

        AnalyticsTrendResponse cached = getFromCache(cacheKey, AnalyticsTrendResponse.class);
        if (cached != null) {
            return cached;
        }

        List<Object[]> rows = dailyMetricRepository.findDailyTrendPoints(range.start(), range.end());
        List<DailyTrendPointDto> points = new ArrayList<>();

        for (Object[] r : rows) {
            points.add(DailyTrendPointDto.builder()
                    .date((LocalDate) r[0])
                    .views(((Number) r[1]).longValue())
                    .plays(((Number) r[2]).longValue())
                    .uniqueViewers(((Number) r[3]).longValue())
                    .watchTimeSeconds(((Number) r[4]).longValue())
                    .completionCount(((Number) r[5]).longValue())
                    .bufferEvents(((Number) r[6]).longValue())
                    .errors(((Number) r[7]).longValue())
                    .build());
        }

        AnalyticsTrendResponse response = AnalyticsTrendResponse.builder()
                .startDate(range.start())
                .endDate(range.end())
                .points(points)
                .build();

        putInCache(cacheKey, response);
        return response;
    }

    public PlatformAnalyticsResponse getPlatformAnalytics(LocalDate startDate, LocalDate endDate) {
        DateRange range = resolveDateRange(startDate, endDate);
        String cacheKey = "communityott:analytics:platforms:" + range.start() + ":" + range.end();

        PlatformAnalyticsResponse cached = getFromCache(cacheKey, PlatformAnalyticsResponse.class);
        if (cached != null) {
            return cached;
        }

        List<Object[]> rows = dailyMetricRepository.findPlatformDistribution(range.start(), range.end());
        List<PlatformMetricDto> platforms = new ArrayList<>();

        for (Object[] r : rows) {
            platforms.add(PlatformMetricDto.builder()
                    .platform((Platform) r[0])
                    .sessions(((Number) r[1]).longValue())
                    .uniqueViewers(((Number) r[2]).longValue())
                    .watchTimeSeconds(((Number) r[3]).longValue())
                    .errors(((Number) r[4]).longValue())
                    .bufferEvents(((Number) r[5]).longValue())
                    .build());
        }

        PlatformAnalyticsResponse response = PlatformAnalyticsResponse.builder()
                .startDate(range.start())
                .endDate(range.end())
                .platforms(platforms)
                .build();

        putInCache(cacheKey, response);
        return response;
    }

    public Page<ContentRankingItemDto> getTopContent(
            LocalDate startDate, LocalDate endDate, String metric, Pageable pageable) {

        DateRange range = resolveDateRange(startDate, endDate);
        String rankingMetric = metric != null ? metric.trim().toUpperCase() : "WATCH_TIME";

        Page<Object[]> page;
        switch (rankingMetric) {
            case "VIEWS" -> page = dailyMetricRepository.findTopContentByViews(range.start(), range.end(), pageable);
            case "UNIQUE_VIEWERS" -> page = dailyMetricRepository.findTopContentByUniqueViewers(range.start(), range.end(), pageable);
            default -> page = dailyMetricRepository.findTopContentByWatchTime(range.start(), range.end(), pageable);
        }

        int startRank = (int) pageable.getOffset() + 1;
        List<ContentRankingItemDto> dtoList = new ArrayList<>();

        for (int i = 0; i < page.getContent().size(); i++) {
            Object[] r = page.getContent().get(i);
            dtoList.add(ContentRankingItemDto.builder()
                    .rank(startRank + i)
                    .contentId(((Number) r[0]).longValue())
                    .title((String) r[1])
                    .thumbnailUrl((String) r[2])
                    .totalViews(((Number) r[3]).longValue())
                    .totalPlays(((Number) r[4]).longValue())
                    .uniqueViewers(((Number) r[5]).longValue())
                    .totalWatchTimeSeconds(((Number) r[6]).longValue())
                    .completionCount(((Number) r[7]).longValue())
                    .build());
        }

        return new PageImpl<>(dtoList, pageable, page.getTotalElements());
    }

    private DateRange resolveDateRange(LocalDate startDate, LocalDate endDate) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate resolvedEnd = endDate != null ? endDate : today;
        LocalDate resolvedStart = startDate != null ? startDate : resolvedEnd.minusDays(6);

        if (resolvedStart.isAfter(resolvedEnd)) {
            throw new InvalidDateRangeException("Start date (" + resolvedStart + ") cannot be after end date (" + resolvedEnd + ")");
        }

        long daysBetween = ChronoUnit.DAYS.between(resolvedStart, resolvedEnd);
        if (daysBetween > MAX_RANGE_DAYS) {
            throw new InvalidDateRangeException("Date range cannot exceed " + MAX_RANGE_DAYS + " days (requested " + daysBetween + " days)");
        }

        return new DateRange(resolvedStart, resolvedEnd);
    }

    @SuppressWarnings("unchecked")
    private <T> T getFromCache(String key, Class<T> clazz) {
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (clazz.isInstance(cached)) {
                return (T) cached;
            }
        } catch (Exception e) {
            log.debug("Redis cache get skipped or failed for key {}: {}", key, e.getMessage());
        }
        return null;
    }

    private void putInCache(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, value, CACHE_TTL);
        } catch (Exception e) {
            log.debug("Redis cache put skipped or failed for key {}: {}", key, e.getMessage());
        }
    }

    private record DateRange(LocalDate start, LocalDate end) {}
}
