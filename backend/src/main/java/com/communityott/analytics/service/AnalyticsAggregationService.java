package com.communityott.analytics.service;

import com.communityott.analytics.dto.AggregationJobResponse;
import com.communityott.analytics.entity.AnalyticsCheckpoint;
import com.communityott.analytics.entity.AnalyticsDailyMetric;
import com.communityott.analytics.repository.AnalyticsCheckpointRepository;
import com.communityott.analytics.repository.AnalyticsDailyMetricRepository;
import com.communityott.auth.entity.Platform;
import com.communityott.content.entity.Content;
import com.communityott.playback.entity.PlaybackEvent;
import com.communityott.playback.entity.PlaybackEventType;
import com.communityott.playback.repository.PlaybackEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsAggregationService {

    public static final String DEFAULT_CONSUMER = "DEFAULT_DAILY_AGGREGATOR";
    private static final String AGGREGATION_LOCK_KEY = "communityott:lock:analytics:aggregation";
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    private final PlaybackEventRepository playbackEventRepository;
    private final AnalyticsDailyMetricRepository dailyMetricRepository;
    private final AnalyticsCheckpointRepository checkpointRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Run aggregation periodically if enabled in configuration.
     */
    @Scheduled(cron = "${communityott.analytics.aggregation.cron:0 */15 * * * *}")
    public void scheduledAggregation() {
        log.debug("Executing scheduled analytics aggregation...");
        try {
            runAggregation(500);
        } catch (Exception e) {
            log.warn("Scheduled analytics aggregation encountered an error: {}", e.getMessage());
        }
    }

    /**
     * Incrementally processes raw playback events since the last recorded checkpoint.
     * Protected by Redis distributed lock.
     */
    @Transactional
    public AggregationJobResponse runAggregation(int batchSize) {
        long startTime = System.currentTimeMillis();

        Boolean acquiredLock = Boolean.FALSE;
        try {
            acquiredLock = redisTemplate.opsForValue().setIfAbsent(AGGREGATION_LOCK_KEY, "LOCKED", LOCK_TTL);
        } catch (Exception e) {
            log.warn("Redis lock unavailable for aggregation, proceeding with single-node safety: {}", e.getMessage());
            acquiredLock = Boolean.TRUE;
        }

        if (Boolean.FALSE.equals(acquiredLock)) {
            log.info("Analytics aggregation skipped: another worker holds the lock");
            return AggregationJobResponse.builder()
                    .status("SKIPPED_LOCKED")
                    .eventsProcessed(0)
                    .lastProcessedEventId(null)
                    .durationMillis(System.currentTimeMillis() - startTime)
                    .message("Aggregation skipped because another process is currently aggregating")
                    .build();
        }

        try {
            AnalyticsCheckpoint checkpoint = checkpointRepository.findByConsumerName(DEFAULT_CONSUMER)
                    .orElseGet(() -> AnalyticsCheckpoint.builder()
                            .consumerName(DEFAULT_CONSUMER)
                            .lastProcessedEventId(0L)
                            .build());

            Long lastId = checkpoint.getLastProcessedEventId() != null ? checkpoint.getLastProcessedEventId() : 0L;
            List<PlaybackEvent> events = playbackEventRepository.findByIdGreaterThanOrderByIdAsc(
                    lastId, PageRequest.of(0, batchSize > 0 ? batchSize : 500));

            if (events.isEmpty()) {
                return AggregationJobResponse.builder()
                        .status("SUCCESS")
                        .eventsProcessed(0)
                        .lastProcessedEventId(lastId)
                        .durationMillis(System.currentTimeMillis() - startTime)
                        .message("No new events to aggregate")
                        .build();
            }

            int processedCount = 0;
            Long newLastId = lastId;

            // In-memory batch aggregation per (Date, ContentId, Platform)
            Map<String, MetricAccumulator> accumulators = new HashMap<>();

            for (PlaybackEvent event : events) {
                if (event.getContent() == null || event.getOccurredAt() == null) {
                    newLastId = Math.max(newLastId, event.getId());
                    processedCount++;
                    continue;
                }

                LocalDate date = LocalDate.ofInstant(event.getOccurredAt(), ZoneOffset.UTC);
                Long contentId = event.getContent().getId();
                Platform platform = event.getPlatform() != null ? event.getPlatform() : Platform.WEB;
                String key = date + ":" + contentId + ":" + platform.name();

                MetricAccumulator acc = accumulators.computeIfAbsent(key, k -> new MetricAccumulator(date, event.getContent(), platform));
                acc.accumulate(event);

                newLastId = Math.max(newLastId, event.getId());
                processedCount++;
            }

            // Persist accumulators into database
            for (MetricAccumulator acc : accumulators.values()) {
                AnalyticsDailyMetric metric = dailyMetricRepository.findByMetricDateAndContentIdAndPlatform(
                        acc.date, acc.content.getId(), acc.platform)
                        .orElseGet(() -> AnalyticsDailyMetric.builder()
                                .metricDate(acc.date)
                                .content(acc.content)
                                .platform(acc.platform)
                                .totalSessions(0)
                                .totalPlays(0)
                                .uniqueViewers(0)
                                .totalWatchTimeSeconds(0L)
                                .completionCount(0)
                                .pauseCount(0)
                                .seekCount(0)
                                .bufferEventCount(0)
                                .errorCount(0)
                                .qualityChangeCount(0)
                                .build());

                metric.setTotalSessions(metric.getTotalSessions() + acc.sessions.size());
                metric.setTotalPlays(metric.getTotalPlays() + acc.plays);
                metric.setUniqueViewers(metric.getUniqueViewers() + acc.uniqueUsers.size());
                metric.setTotalWatchTimeSeconds(metric.getTotalWatchTimeSeconds() + acc.watchTimeSeconds);
                metric.setCompletionCount(metric.getCompletionCount() + acc.completions);
                metric.setPauseCount(metric.getPauseCount() + acc.pauses);
                metric.setSeekCount(metric.getSeekCount() + acc.seeks);
                metric.setBufferEventCount(metric.getBufferEventCount() + acc.buffers);
                metric.setErrorCount(metric.getErrorCount() + acc.errors);
                metric.setQualityChangeCount(metric.getQualityChangeCount() + acc.qualityChanges);

                dailyMetricRepository.save(metric);
            }

            // Update checkpoint
            checkpoint.setLastProcessedEventId(newLastId);
            checkpoint.setLastProcessedOccurredAt(events.get(events.size() - 1).getOccurredAt());
            checkpointRepository.save(checkpoint);

            // Invalidate analytics caches
            invalidateAnalyticsCache();

            long duration = System.currentTimeMillis() - startTime;
            log.info("Analytics aggregation complete: {} events processed in {} ms. Checkpoint: {}", processedCount, duration, newLastId);

            return AggregationJobResponse.builder()
                    .status("SUCCESS")
                    .eventsProcessed(processedCount)
                    .lastProcessedEventId(newLastId)
                    .durationMillis(duration)
                    .message("Successfully aggregated " + processedCount + " playback events")
                    .build();

        } finally {
            try {
                redisTemplate.delete(AGGREGATION_LOCK_KEY);
            } catch (Exception ignored) {}
        }
    }

    private void invalidateAnalyticsCache() {
        try {
            Set<String> keys = redisTemplate.keys("communityott:analytics:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.debug("Redis cache invalidation skipped or failed: {}", e.getMessage());
        }
    }

    private static class MetricAccumulator {
        final LocalDate date;
        final Content content;
        final Platform platform;

        final Set<Long> sessions = new HashSet<>();
        final Set<Long> uniqueUsers = new HashSet<>();
        int plays = 0;
        long watchTimeSeconds = 0L;
        int completions = 0;
        int pauses = 0;
        int seeks = 0;
        int buffers = 0;
        int errors = 0;
        int qualityChanges = 0;

        MetricAccumulator(LocalDate date, Content content, Platform platform) {
            this.date = date;
            this.content = content;
            this.platform = platform;
        }

        void accumulate(PlaybackEvent event) {
            if (event.getPlaybackSession() != null) {
                sessions.add(event.getPlaybackSession().getId());
            }
            if (event.getUser() != null) {
                uniqueUsers.add(event.getUser().getId());
            }

            if (event.getEventType() != null) {
                switch (event.getEventType()) {
                    case PLAY -> {
                        plays++;
                        if (event.getPositionSeconds() > 0) {
                            watchTimeSeconds += event.getPositionSeconds();
                        }
                    }
                    case COMPLETE -> {
                        completions++;
                        if (event.getPositionSeconds() > 0) {
                            watchTimeSeconds += event.getPositionSeconds();
                        }
                    }
                    case PAUSE -> pauses++;
                    case SEEK -> seeks++;
                    case BUFFER_START -> buffers++;
                    case ERROR -> errors++;
                    case QUALITY_CHANGE -> qualityChanges++;
                    case HEARTBEAT -> {
                        // Heartbeats typically occur every 30-60s during playback
                        watchTimeSeconds += 30L;
                    }
                    default -> {}
                }
            }
        }
    }
}
