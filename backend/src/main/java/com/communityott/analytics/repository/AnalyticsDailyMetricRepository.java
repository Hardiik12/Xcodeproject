package com.communityott.analytics.repository;

import com.communityott.analytics.entity.AnalyticsDailyMetric;
import com.communityott.auth.entity.Platform;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AnalyticsDailyMetricRepository extends JpaRepository<AnalyticsDailyMetric, Long> {

    Optional<AnalyticsDailyMetric> findByMetricDateAndContentIdAndPlatform(
            LocalDate metricDate, Long contentId, Platform platform);

    List<AnalyticsDailyMetric> findByMetricDateBetween(LocalDate startDate, LocalDate endDate);

    List<AnalyticsDailyMetric> findByContentIdAndMetricDateBetween(Long contentId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT m.metricDate AS metricDate, " +
            "SUM(m.totalSessions) AS totalSessions, " +
            "SUM(m.totalPlays) AS totalPlays, " +
            "SUM(m.uniqueViewers) AS uniqueViewers, " +
            "SUM(m.totalWatchTimeSeconds) AS totalWatchTimeSeconds, " +
            "SUM(m.completionCount) AS completionCount, " +
            "SUM(m.bufferEventCount) AS bufferEventCount, " +
            "SUM(m.errorCount) AS errorCount " +
            "FROM AnalyticsDailyMetric m " +
            "WHERE m.metricDate BETWEEN :startDate AND :endDate " +
            "GROUP BY m.metricDate " +
            "ORDER BY m.metricDate ASC")
    List<Object[]> findDailyTrendPoints(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT m.platform AS platform, " +
            "SUM(m.totalSessions) AS totalSessions, " +
            "SUM(m.uniqueViewers) AS uniqueViewers, " +
            "SUM(m.totalWatchTimeSeconds) AS totalWatchTimeSeconds, " +
            "SUM(m.errorCount) AS errorCount, " +
            "SUM(m.bufferEventCount) AS bufferEventCount " +
            "FROM AnalyticsDailyMetric m " +
            "WHERE m.metricDate BETWEEN :startDate AND :endDate " +
            "GROUP BY m.platform")
    List<Object[]> findPlatformDistribution(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT m.content.id AS contentId, " +
            "m.content.title AS title, " +
            "m.content.thumbnailUrl AS thumbnailUrl, " +
            "SUM(m.totalSessions) AS totalSessions, " +
            "SUM(m.totalPlays) AS totalPlays, " +
            "SUM(m.uniqueViewers) AS uniqueViewers, " +
            "SUM(m.totalWatchTimeSeconds) AS totalWatchTimeSeconds, " +
            "SUM(m.completionCount) AS completionCount " +
            "FROM AnalyticsDailyMetric m " +
            "WHERE m.metricDate BETWEEN :startDate AND :endDate " +
            "GROUP BY m.content.id, m.content.title, m.content.thumbnailUrl " +
            "ORDER BY SUM(m.totalWatchTimeSeconds) DESC")
    Page<Object[]> findTopContentByWatchTime(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);

    @Query("SELECT m.content.id AS contentId, " +
            "m.content.title AS title, " +
            "m.content.thumbnailUrl AS thumbnailUrl, " +
            "SUM(m.totalSessions) AS totalSessions, " +
            "SUM(m.totalPlays) AS totalPlays, " +
            "SUM(m.uniqueViewers) AS uniqueViewers, " +
            "SUM(m.totalWatchTimeSeconds) AS totalWatchTimeSeconds, " +
            "SUM(m.completionCount) AS completionCount " +
            "FROM AnalyticsDailyMetric m " +
            "WHERE m.metricDate BETWEEN :startDate AND :endDate " +
            "GROUP BY m.content.id, m.content.title, m.content.thumbnailUrl " +
            "ORDER BY SUM(m.totalSessions) DESC")
    Page<Object[]> findTopContentByViews(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);

    @Query("SELECT m.content.id AS contentId, " +
            "m.content.title AS title, " +
            "m.content.thumbnailUrl AS thumbnailUrl, " +
            "SUM(m.totalSessions) AS totalSessions, " +
            "SUM(m.totalPlays) AS totalPlays, " +
            "SUM(m.uniqueViewers) AS uniqueViewers, " +
            "SUM(m.totalWatchTimeSeconds) AS totalWatchTimeSeconds, " +
            "SUM(m.completionCount) AS completionCount " +
            "FROM AnalyticsDailyMetric m " +
            "WHERE m.metricDate BETWEEN :startDate AND :endDate " +
            "GROUP BY m.content.id, m.content.title, m.content.thumbnailUrl " +
            "ORDER BY SUM(m.uniqueViewers) DESC")
    Page<Object[]> findTopContentByUniqueViewers(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);
}
