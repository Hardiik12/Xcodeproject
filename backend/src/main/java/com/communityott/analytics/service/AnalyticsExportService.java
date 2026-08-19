package com.communityott.analytics.service;

import com.communityott.analytics.dto.AnalyticsExportRecordDto;
import com.communityott.analytics.dto.AnalyticsExportResponse;
import com.communityott.analytics.repository.AnalyticsDailyMetricRepository;
import com.communityott.auth.entity.Platform;
import com.communityott.common.exception.AnalyticsInvalidPaginationException;
import com.communityott.common.exception.AnalyticsInvalidPlatformException;
import com.communityott.common.exception.InvalidDateRangeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsExportService {

    private static final int MAX_RANGE_DAYS = 90;
    private static final int DEFAULT_SIZE = 100;
    private static final int MAX_SIZE = 100;

    private final AnalyticsDailyMetricRepository dailyMetricRepository;

    public AnalyticsExportResponse exportMetrics(
            LocalDate from,
            LocalDate to,
            String platformStr,
            Long contentId,
            Long categoryId,
            Long languageId,
            int page,
            int size) {

        // Validate pagination
        validatePagination(page, size);

        // Resolve date range (Aggregation timezone is UTC)
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate resolvedTo = to != null ? to : today;
        LocalDate resolvedFrom = from != null ? from : resolvedTo.minusDays(6);

        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new InvalidDateRangeException("Start date ('from': " + resolvedFrom + ") cannot be after end date ('to': " + resolvedTo + ")");
        }

        long daysBetween = ChronoUnit.DAYS.between(resolvedFrom, resolvedTo);
        if (daysBetween > MAX_RANGE_DAYS) {
            throw new InvalidDateRangeException("Date range cannot exceed " + MAX_RANGE_DAYS + " days (requested " + daysBetween + " days)");
        }

        // Validate platform if provided
        Platform platform = parsePlatform(platformStr);

        Pageable pageable = PageRequest.of(page, size);
        Page<Object[]> dbPage = dailyMetricRepository.findExportRecords(
                resolvedFrom, resolvedTo, platform, contentId, categoryId, languageId, pageable);

        List<AnalyticsExportRecordDto> records = new ArrayList<>();
        for (Object[] r : dbPage.getContent()) {
            LocalDate date = (LocalDate) r[0];
            Long cId = ((Number) r[1]).longValue();
            Long catId = r[2] != null ? ((Number) r[2]).longValue() : null;
            Long langId = r[3] != null ? ((Number) r[3]).longValue() : null;
            Platform p = (Platform) r[4];
            long sessions = ((Number) r[5]).longValue();
            long plays = ((Number) r[6]).longValue();
            long uniqueViewers = ((Number) r[7]).longValue();
            long watchTime = ((Number) r[8]).longValue();
            long completedPlays = ((Number) r[9]).longValue();
            long bufferEvents = ((Number) r[10]).longValue();
            long errors = ((Number) r[11]).longValue();
            long qualityChanges = ((Number) r[12]).longValue();

            double completionRate = plays > 0
                    ? Math.round(((double) completedPlays / plays) * 10000.0) / 10000.0
                    : 0.0;

            records.add(AnalyticsExportRecordDto.builder()
                    .date(date.toString())
                    .contentId(cId)
                    .categoryId(catId)
                    .languageId(langId)
                    .platform(p != null ? p.name() : null)
                    .sessions(sessions)
                    .plays(plays)
                    .uniqueViewers(uniqueViewers)
                    .watchTimeSeconds(watchTime)
                    .completedPlays(completedPlays)
                    .completionRate(completionRate)
                    .bufferingEvents(bufferEvents)
                    .playbackErrors(errors)
                    .qualityChanges(qualityChanges)
                    .build());
        }

        return AnalyticsExportResponse.builder()
                .contractVersion("analytics-contract-v1")
                .generatedAt(Instant.now())
                .from(resolvedFrom)
                .to(resolvedTo)
                .page(page)
                .size(size)
                .totalRecords(dbPage.getTotalElements())
                .totalPages(dbPage.getTotalPages())
                .hasNext(dbPage.hasNext())
                .records(records)
                .build();
    }

    private Platform parsePlatform(String platformStr) {
        if (platformStr == null || platformStr.isBlank()) {
            return null;
        }
        try {
            return Platform.valueOf(platformStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AnalyticsInvalidPlatformException(platformStr);
        }
    }

    private void validatePagination(int page, int size) {
        if (page < 0) {
            throw new AnalyticsInvalidPaginationException("Page index cannot be negative (requested: " + page + ")");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new AnalyticsInvalidPaginationException("Page size must be between 1 and " + MAX_SIZE + " (requested: " + size + ")");
        }
    }
}
