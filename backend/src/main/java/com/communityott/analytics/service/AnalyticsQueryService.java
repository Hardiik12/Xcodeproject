package com.communityott.analytics.service;

import com.communityott.analytics.dto.AdminSystemAnalyticsResponse;
import com.communityott.analytics.dto.AdminUserAnalyticsResponse;
import com.communityott.analytics.dto.AnalyticsOverviewResponse;
import com.communityott.analytics.dto.AnalyticsTrendResponse;
import com.communityott.analytics.dto.CategoryAnalyticsResponse;
import com.communityott.analytics.dto.CategoryMetricDto;
import com.communityott.analytics.dto.ContentAnalyticsResponse;
import com.communityott.analytics.dto.ContentRankingItemDto;
import com.communityott.analytics.dto.DailyTrendPointDto;
import com.communityott.analytics.dto.LanguageAnalyticsResponse;
import com.communityott.analytics.dto.LanguageMetricDto;
import com.communityott.analytics.dto.ManagerOverviewResponse;
import com.communityott.analytics.dto.PeriodComparisonDto;
import com.communityott.analytics.dto.PlatformAnalyticsResponse;
import com.communityott.analytics.dto.PlatformMetricDto;
import com.communityott.analytics.entity.AnalyticsDailyMetric;
import com.communityott.analytics.repository.AnalyticsDailyMetricRepository;
import com.communityott.auth.entity.Platform;
import com.communityott.common.exception.AnalyticsInvalidPaginationException;
import com.communityott.common.exception.AnalyticsInvalidPlatformException;
import com.communityott.common.exception.AnalyticsInvalidSortException;
import com.communityott.common.exception.ContentNotFoundException;
import com.communityott.common.exception.InvalidDateRangeException;
import com.communityott.content.entity.Content;
import com.communityott.content.entity.ContentStatus;
import com.communityott.content.repository.ContentRepository;
import com.communityott.content.repository.VideoAssetRepository;
import com.communityott.playback.repository.PlaybackSessionRepository;
import com.communityott.user.entity.UserStatus;
import com.communityott.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsQueryService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(60);
    private static final int MAX_RANGE_DAYS = 90;
    private static final Set<String> VALID_SORT_FIELDS = Set.of("WATCH_TIME", "VIEWS", "UNIQUE_VIEWERS", "COMPLETIONS");
    private static final Set<String> VALID_SORT_DIRECTIONS = Set.of("ASC", "DESC");

    private final AnalyticsDailyMetricRepository dailyMetricRepository;
    private final ContentRepository contentRepository;
    private final VideoAssetRepository videoAssetRepository;
    private final UserRepository userRepository;
    private final PlaybackSessionRepository playbackSessionRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    public AnalyticsOverviewResponse getOverview(LocalDate startDate, LocalDate endDate) {
        return getOverview(startDate, endDate, null, null);
    }

    public AnalyticsOverviewResponse getOverview(LocalDate startDate, LocalDate endDate, String timeWindow, String platformStr) {
        DateRange range = resolveDateRange(startDate, endDate, timeWindow);
        Platform platform = parsePlatform(platformStr);

        String platformKey = platform != null ? platform.name() : "ALL";
        String cacheKey = "communityott:analytics:overview:" + range.start() + ":" + range.end() + ":" + platformKey;

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
            if (platform == null || m.getPlatform() == platform) {
                totalViews += m.getTotalSessions();
                totalPlays += m.getTotalPlays();
                uniqueViewers += m.getUniqueViewers();
                totalWatchTime += m.getTotalWatchTimeSeconds();
                completedPlays += m.getCompletionCount();
                bufferEvents += m.getBufferEventCount();
                errors += m.getErrorCount();
                qualityChanges += m.getQualityChangeCount();
            }
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

    public ManagerOverviewResponse getManagerOverview(LocalDate startDate, LocalDate endDate, String timeWindow, String platformStr) {
        DateRange range = resolveDateRange(startDate, endDate, timeWindow);
        Platform platform = parsePlatform(platformStr);

        long daysBetween = ChronoUnit.DAYS.between(range.start(), range.end()) + 1;
        LocalDate prevStart = range.start().minusDays(daysBetween);
        LocalDate prevEnd = range.start().minusDays(1);

        String platformKey = platform != null ? platform.name() : "ALL";
        String cacheKey = "communityott:analytics:manager:overview:" + range.start() + ":" + range.end() + ":" + platformKey;

        ManagerOverviewResponse cached = getFromCache(cacheKey, ManagerOverviewResponse.class);
        if (cached != null) {
            return cached;
        }

        // Current period aggregation
        List<AnalyticsDailyMetric> currentMetrics = dailyMetricRepository.findByMetricDateBetween(range.start(), range.end());
        long curViews = 0, curPlays = 0, curViewers = 0, curWatchTime = 0, curCompletions = 0, curBuffers = 0, curErrors = 0, curQualities = 0;
        for (AnalyticsDailyMetric m : currentMetrics) {
            if (platform == null || m.getPlatform() == platform) {
                curViews += m.getTotalSessions();
                curPlays += m.getTotalPlays();
                curViewers += m.getUniqueViewers();
                curWatchTime += m.getTotalWatchTimeSeconds();
                curCompletions += m.getCompletionCount();
                curBuffers += m.getBufferEventCount();
                curErrors += m.getErrorCount();
                curQualities += m.getQualityChangeCount();
            }
        }

        // Previous period aggregation
        List<AnalyticsDailyMetric> prevMetrics = dailyMetricRepository.findByMetricDateBetween(prevStart, prevEnd);
        long prevViews = 0, prevPlays = 0, prevViewers = 0, prevWatchTime = 0, prevCompletions = 0;
        for (AnalyticsDailyMetric m : prevMetrics) {
            if (platform == null || m.getPlatform() == platform) {
                prevViews += m.getTotalSessions();
                prevPlays += m.getTotalPlays();
                prevViewers += m.getUniqueViewers();
                prevWatchTime += m.getTotalWatchTimeSeconds();
                prevCompletions += m.getCompletionCount();
            }
        }

        double curCompRate = curViews > 0 ? Math.round(((double) curCompletions / curViews) * 100.0) / 100.0 : 0.0;
        double prevCompRate = prevViews > 0 ? Math.round(((double) prevCompletions / prevViews) * 100.0) / 100.0 : 0.0;
        double compRateGrowth;
        if (prevCompRate > 0.0) {
            compRateGrowth = Math.round(((curCompRate - prevCompRate) / prevCompRate) * 10000.0) / 100.0;
        } else if (curCompRate > 0.0) {
            compRateGrowth = 100.0;
        } else {
            compRateGrowth = 0.0;
        }

        Page<ContentRankingItemDto> topPage = getTopContent(range.start(), range.end(), null, platformStr, null, null, "WATCH_TIME", "DESC", 0, 5);

        ManagerOverviewResponse response = ManagerOverviewResponse.builder()
                .startDate(range.start())
                .endDate(range.end())
                .previousStartDate(prevStart)
                .previousEndDate(prevEnd)
                .views(PeriodComparisonDto.of(curViews, prevViews))
                .plays(PeriodComparisonDto.of(curPlays, prevPlays))
                .uniqueViewers(PeriodComparisonDto.of(curViewers, prevViewers))
                .watchTimeSeconds(PeriodComparisonDto.of(curWatchTime, prevWatchTime))
                .completedPlays(PeriodComparisonDto.of(curCompletions, prevCompletions))
                .currentCompletionRate(curCompRate)
                .previousCompletionRate(prevCompRate)
                .completionRateGrowthPercentage(compRateGrowth)
                .bufferEvents(curBuffers)
                .playbackErrors(curErrors)
                .qualityChanges(curQualities)
                .topContent(topPage.getContent())
                .build();

        putInCache(cacheKey, response);
        return response;
    }

    public CategoryAnalyticsResponse getCategoryAnalytics(LocalDate startDate, LocalDate endDate, String timeWindow) {
        DateRange range = resolveDateRange(startDate, endDate, timeWindow);
        String cacheKey = "communityott:analytics:manager:categories:" + range.start() + ":" + range.end();

        CategoryAnalyticsResponse cached = getFromCache(cacheKey, CategoryAnalyticsResponse.class);
        if (cached != null) {
            return cached;
        }

        List<Object[]> rows = dailyMetricRepository.findCategoryPerformance(range.start(), range.end());
        List<CategoryMetricDto> categories = new ArrayList<>();

        for (Object[] r : rows) {
            long views = ((Number) r[3]).longValue();
            long completions = ((Number) r[7]).longValue();
            double compRate = views > 0 ? Math.round(((double) completions / views) * 100.0) / 100.0 : 0.0;

            categories.add(CategoryMetricDto.builder()
                    .categoryId(((Number) r[0]).longValue())
                    .categoryName((String) r[1])
                    .slug((String) r[2])
                    .totalViews(views)
                    .totalPlays(((Number) r[4]).longValue())
                    .uniqueViewers(((Number) r[5]).longValue())
                    .totalWatchTimeSeconds(((Number) r[6]).longValue())
                    .completedPlays(completions)
                    .completionRate(compRate)
                    .build());
        }

        CategoryAnalyticsResponse response = CategoryAnalyticsResponse.builder()
                .startDate(range.start())
                .endDate(range.end())
                .categories(categories)
                .build();

        putInCache(cacheKey, response);
        return response;
    }

    public LanguageAnalyticsResponse getLanguageAnalytics(LocalDate startDate, LocalDate endDate, String timeWindow) {
        DateRange range = resolveDateRange(startDate, endDate, timeWindow);
        String cacheKey = "communityott:analytics:manager:languages:" + range.start() + ":" + range.end();

        LanguageAnalyticsResponse cached = getFromCache(cacheKey, LanguageAnalyticsResponse.class);
        if (cached != null) {
            return cached;
        }

        List<Object[]> rows = dailyMetricRepository.findLanguagePerformance(range.start(), range.end());
        List<LanguageMetricDto> languages = new ArrayList<>();

        for (Object[] r : rows) {
            long views = ((Number) r[3]).longValue();
            long completions = ((Number) r[7]).longValue();
            double compRate = views > 0 ? Math.round(((double) completions / views) * 100.0) / 100.0 : 0.0;

            languages.add(LanguageMetricDto.builder()
                    .languageId(((Number) r[0]).longValue())
                    .languageName((String) r[1])
                    .languageCode((String) r[2])
                    .totalViews(views)
                    .totalPlays(((Number) r[4]).longValue())
                    .uniqueViewers(((Number) r[5]).longValue())
                    .totalWatchTimeSeconds(((Number) r[6]).longValue())
                    .completedPlays(completions)
                    .completionRate(compRate)
                    .build());
        }

        LanguageAnalyticsResponse response = LanguageAnalyticsResponse.builder()
                .startDate(range.start())
                .endDate(range.end())
                .languages(languages)
                .build();

        putInCache(cacheKey, response);
        return response;
    }

    public AdminSystemAnalyticsResponse getAdminSystemAnalytics() {
        String cacheKey = "communityott:analytics:admin:system";

        AdminSystemAnalyticsResponse cached = getFromCache(cacheKey, AdminSystemAnalyticsResponse.class);
        if (cached != null) {
            return cached;
        }

        long registeredUsers = userRepository.count();
        long activeUsers = userRepository.countByStatus(UserStatus.ACTIVE);
        long publishedContent = contentRepository.countByStatus(ContentStatus.PUBLISHED);
        long totalContent = contentRepository.count();
        long videoAssets = videoAssetRepository.count();
        long playbackSessions = playbackSessionRepository.count();

        List<Object[]> lifetimeRows = dailyMetricRepository.findLifetimeMetrics();
        long lifetimePlays = 0;
        long lifetimeViewers = 0;
        long lifetimeWatchTime = 0;
        if (!lifetimeRows.isEmpty()) {
            Object[] r = lifetimeRows.get(0);
            lifetimePlays = ((Number) r[0]).longValue();
            lifetimeViewers = ((Number) r[1]).longValue();
            lifetimeWatchTime = ((Number) r[2]).longValue();
        }

        Map<Platform, PlatformMetricDto> platformMap = new EnumMap<>(Platform.class);
        for (Platform p : Platform.values()) {
            platformMap.put(p, PlatformMetricDto.builder()
                    .platform(p)
                    .sessions(0)
                    .totalPlays(0)
                    .uniqueViewers(0)
                    .totalWatchTimeSeconds(0)
                    .completionCount(0)
                    .errors(0)
                    .bufferEvents(0)
                    .build());
        }

        List<Object[]> platformRows = dailyMetricRepository.findPlatformDistribution(LocalDate.of(2000, 1, 1), LocalDate.now(ZoneOffset.UTC));
        for (Object[] r : platformRows) {
            Platform p = (Platform) r[0];
            if (p != null && platformMap.containsKey(p)) {
                platformMap.put(p, PlatformMetricDto.builder()
                        .platform(p)
                        .sessions(((Number) r[1]).longValue())
                        .totalPlays(((Number) r[2]).longValue())
                        .uniqueViewers(((Number) r[3]).longValue())
                        .totalWatchTimeSeconds(((Number) r[4]).longValue())
                        .completionCount(((Number) r[5]).longValue())
                        .errors(((Number) r[6]).longValue())
                        .bufferEvents(((Number) r[7]).longValue())
                        .build());
            }
        }

        AdminSystemAnalyticsResponse response = AdminSystemAnalyticsResponse.builder()
                .generatedAt(Instant.now())
                .totalRegisteredUsers(registeredUsers)
                .totalActiveUsers(activeUsers)
                .totalPublishedContent(publishedContent)
                .totalActiveContent(totalContent)
                .totalVideoAssets(videoAssets)
                .totalPlaybackSessions(playbackSessions)
                .lifetimePlays(lifetimePlays)
                .lifetimeWatchTimeSeconds(lifetimeWatchTime)
                .lifetimeUniqueViewers(lifetimeViewers)
                .platformSummary(new ArrayList<>(platformMap.values()))
                .build();


        putInCache(cacheKey, response);
        return response;
    }

    public AdminUserAnalyticsResponse getAdminUserAnalytics(LocalDate startDate, LocalDate endDate, String timeWindow) {
        DateRange range = resolveDateRange(startDate, endDate, timeWindow);
        String cacheKey = "communityott:analytics:admin:users:" + range.start() + ":" + range.end();

        AdminUserAnalyticsResponse cached = getFromCache(cacheKey, AdminUserAnalyticsResponse.class);
        if (cached != null) {
            return cached;
        }

        long registeredUsers = userRepository.count();
        long activeUsers = userRepository.countByStatus(UserStatus.ACTIVE);

        List<AnalyticsDailyMetric> metrics = dailyMetricRepository.findByMetricDateBetween(range.start(), range.end());
        long uniqueViewers = 0;
        long totalSessions = 0;
        long totalWatchTime = 0;

        for (AnalyticsDailyMetric m : metrics) {
            uniqueViewers += m.getUniqueViewers();
            totalSessions += m.getTotalSessions();
            totalWatchTime += m.getTotalWatchTimeSeconds();
        }

        long avgWatchTime = uniqueViewers > 0 ? totalWatchTime / uniqueViewers : 0;

        AdminUserAnalyticsResponse response = AdminUserAnalyticsResponse.builder()
                .startDate(range.start())
                .endDate(range.end())
                .totalRegisteredUsers(registeredUsers)
                .activeUsers(activeUsers)
                .activeViewersInPeriod(uniqueViewers)
                .totalSessionsInPeriod(totalSessions)
                .totalWatchTimeSecondsInPeriod(totalWatchTime)
                .averageWatchTimePerViewerSeconds(avgWatchTime)
                .build();

        putInCache(cacheKey, response);
        return response;
    }

    public ContentAnalyticsResponse getContentAnalytics(Long contentId, LocalDate startDate, LocalDate endDate) {
        return getContentAnalytics(contentId, startDate, endDate, null);
    }

    public ContentAnalyticsResponse getContentAnalytics(Long contentId, LocalDate startDate, LocalDate endDate, String timeWindow) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new ContentNotFoundException(contentId));

        DateRange range = resolveDateRange(startDate, endDate, timeWindow);
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
        return getDailyTrends(startDate, endDate, null);
    }

    public AnalyticsTrendResponse getDailyTrends(LocalDate startDate, LocalDate endDate, String timeWindow) {
        DateRange range = resolveDateRange(startDate, endDate, timeWindow);
        String cacheKey = "communityott:analytics:trends:" + range.start() + ":" + range.end();

        AnalyticsTrendResponse cached = getFromCache(cacheKey, AnalyticsTrendResponse.class);
        if (cached != null) {
            return cached;
        }

        List<Object[]> rows = dailyMetricRepository.findDailyTrendPoints(range.start(), range.end());
        List<DailyTrendPointDto> points = new ArrayList<>();

        for (Object[] r : rows) {
            LocalDate date = (LocalDate) r[0];
            points.add(DailyTrendPointDto.builder()
                    .date(date)
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
        return getPlatformAnalytics(startDate, endDate, null);
    }

    public PlatformAnalyticsResponse getPlatformAnalytics(LocalDate startDate, LocalDate endDate, String timeWindow) {
        DateRange range = resolveDateRange(startDate, endDate, timeWindow);
        String cacheKey = "communityott:analytics:platforms:" + range.start() + ":" + range.end();

        PlatformAnalyticsResponse cached = getFromCache(cacheKey, PlatformAnalyticsResponse.class);
        if (cached != null) {
            return cached;
        }

        Map<Platform, PlatformMetricDto> platformMap = new EnumMap<>(Platform.class);
        for (Platform p : Platform.values()) {
            platformMap.put(p, PlatformMetricDto.builder()
                    .platform(p)
                    .sessions(0)
                    .totalPlays(0)
                    .uniqueViewers(0)
                    .totalWatchTimeSeconds(0)
                    .completionCount(0)
                    .errors(0)
                    .bufferEvents(0)
                    .build());
        }

        List<Object[]> rows = dailyMetricRepository.findPlatformDistribution(range.start(), range.end());
        for (Object[] r : rows) {
            Platform p = (Platform) r[0];
            if (p != null && platformMap.containsKey(p)) {
                platformMap.put(p, PlatformMetricDto.builder()
                        .platform(p)
                        .sessions(((Number) r[1]).longValue())
                        .totalPlays(((Number) r[2]).longValue())
                        .uniqueViewers(((Number) r[3]).longValue())
                        .totalWatchTimeSeconds(((Number) r[4]).longValue())
                        .completionCount(((Number) r[5]).longValue())
                        .errors(((Number) r[6]).longValue())
                        .bufferEvents(((Number) r[7]).longValue())
                        .build());
            }
        }

        PlatformAnalyticsResponse response = PlatformAnalyticsResponse.builder()
                .startDate(range.start())
                .endDate(range.end())
                .platforms(new ArrayList<>(platformMap.values()))
                .build();

        putInCache(cacheKey, response);
        return response;
    }

    public Page<ContentRankingItemDto> getTopContent(LocalDate startDate, LocalDate endDate, String metric, Pageable pageable) {
        return getTopContent(startDate, endDate, null, null, null, null, metric, "DESC", pageable.getPageNumber(), pageable.getPageSize());
    }

    public Page<ContentRankingItemDto> getTopContent(
            LocalDate startDate, LocalDate endDate, String timeWindow,
            String platformStr, Long categoryId, Long languageId,
            String sortByStr, String sortDirectionStr, int page, int size) {

        validatePagination(page, size);
        DateRange range = resolveDateRange(startDate, endDate, timeWindow);
        Platform platform = parsePlatform(platformStr);

        String sortBy = (sortByStr != null && !sortByStr.isBlank()) ? sortByStr.trim().toUpperCase() : "WATCH_TIME";
        if (!VALID_SORT_FIELDS.contains(sortBy)) {
            throw new AnalyticsInvalidSortException("Invalid sortBy field: '" + sortByStr + "'. Supported fields are: " + VALID_SORT_FIELDS);
        }

        String sortDirection = (sortDirectionStr != null && !sortDirectionStr.isBlank()) ? sortDirectionStr.trim().toUpperCase() : "DESC";
        if (!VALID_SORT_DIRECTIONS.contains(sortDirection)) {
            throw new AnalyticsInvalidSortException("Invalid sortDirection: '" + sortDirectionStr + "'. Supported directions are: ASC, DESC");
        }

        String cacheKey = "communityott:analytics:top:" + range.start() + ":" + range.end() + ":" +
                (platform != null ? platform.name() : "ALL") + ":" +
                (categoryId != null ? categoryId : "ALL") + ":" +
                (languageId != null ? languageId : "ALL") + ":" +
                sortBy + ":" + sortDirection + ":" + page + ":" + size;

        @SuppressWarnings("unchecked")
        Page<ContentRankingItemDto> cached = getFromCache(cacheKey, Page.class);
        if (cached != null) {
            return cached;
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<Object[]> dbPage;

        boolean isDesc = "DESC".equals(sortDirection);
        switch (sortBy) {
            case "VIEWS" -> dbPage = isDesc
                    ? dailyMetricRepository.findTopContentByPlaysDesc(range.start(), range.end(), platform, categoryId, languageId, pageable)
                    : dailyMetricRepository.findTopContentByPlaysAsc(range.start(), range.end(), platform, categoryId, languageId, pageable);
            case "UNIQUE_VIEWERS" -> dbPage = isDesc
                    ? dailyMetricRepository.findTopContentByUniqueViewersDesc(range.start(), range.end(), platform, categoryId, languageId, pageable)
                    : dailyMetricRepository.findTopContentByUniqueViewersAsc(range.start(), range.end(), platform, categoryId, languageId, pageable);
            case "COMPLETIONS" -> dbPage = isDesc
                    ? dailyMetricRepository.findTopContentByCompletionsDesc(range.start(), range.end(), platform, categoryId, languageId, pageable)
                    : dailyMetricRepository.findTopContentByCompletionsAsc(range.start(), range.end(), platform, categoryId, languageId, pageable);
            default -> dbPage = isDesc
                    ? dailyMetricRepository.findTopContentByWatchTimeDesc(range.start(), range.end(), platform, categoryId, languageId, pageable)
                    : dailyMetricRepository.findTopContentByWatchTimeAsc(range.start(), range.end(), platform, categoryId, languageId, pageable);
        }

        int startRank = (int) pageable.getOffset() + 1;
        List<ContentRankingItemDto> dtoList = new ArrayList<>();

        for (int i = 0; i < dbPage.getContent().size(); i++) {
            Object[] r = dbPage.getContent().get(i);
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

        Page<ContentRankingItemDto> resultPage = new PageImpl<>(dtoList, pageable, dbPage.getTotalElements());
        putInCache(cacheKey, resultPage);
        return resultPage;
    }

    public DateRange resolveDateRange(LocalDate startDate, LocalDate endDate, String timeWindow) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        if (timeWindow != null && !timeWindow.isBlank()) {
            String window = timeWindow.trim().toUpperCase();
            switch (window) {
                case "TODAY" -> {
                    return new DateRange(today, today);
                }
                case "YESTERDAY" -> {
                    LocalDate y = today.minusDays(1);
                    return new DateRange(y, y);
                }
                case "LAST_7_DAYS" -> {
                    return new DateRange(today.minusDays(6), today);
                }
                case "LAST_30_DAYS" -> {
                    return new DateRange(today.minusDays(29), today);
                }
                case "CUSTOM" -> {
                    // Fallthrough to custom dates
                }
                default -> throw new InvalidDateRangeException("Invalid timeWindow: '" + timeWindow + "'. Supported: TODAY, YESTERDAY, LAST_7_DAYS, LAST_30_DAYS, CUSTOM");
            }
        }

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

    public Platform parsePlatform(String platformStr) {
        if (platformStr == null || platformStr.isBlank()) {
            return null;
        }
        try {
            return Platform.valueOf(platformStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AnalyticsInvalidPlatformException(platformStr);
        }
    }

    public void validatePagination(int page, int size) {
        if (page < 0) {
            throw new AnalyticsInvalidPaginationException("Page index cannot be negative (requested: " + page + ")");
        }
        if (size < 1 || size > 100) {
            throw new AnalyticsInvalidPaginationException("Page size must be between 1 and 100 (requested: " + size + ")");
        }
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

    public record DateRange(LocalDate start, LocalDate end) {}
}
