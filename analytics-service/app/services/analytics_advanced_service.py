"""Service layer for Phase 7.4 Advanced Statistical and Business Analytics."""

import logging
from datetime import date
from typing import List, Optional, Union
import pandas as pd

from app.analytics.anomalies import detect_anomalies_iqr
from app.analytics.distributions import calculate_dataset_distributions
from app.analytics.engagement import calculate_engagement_metrics
from app.analytics.growth import calculate_daily_trends, calculate_period_growth
from app.analytics.insights import evaluate_content_insights
from app.analytics.performance import calculate_content_performance_scores
from app.clients.analytics_client import AnalyticsClient
from app.core.config import Settings, get_settings
from app.processing.aggregations import (
    aggregate_by_category,
    aggregate_by_language,
    aggregate_by_platform,
)
from app.processing.data_cleaner import clean_dataframe
from app.processing.data_validator import validate_dataframe
from app.processing.dataframe_builder import build_dataframe
from app.schemas.advanced import (
    AnomalyDetectionResponse,
    AnomalyItem,
    CategoryComparisonItem,
    ContentPerformanceScoreItem,
    DailyTrendItem,
    DistributionAnalysisResponse,
    DistributionMetricItem,
    EngagementAnalyticsResponse,
    GrowthAnalysisResponse,
    GrowthMetricItem,
    InsightItem,
    InsightsResponse,
    LanguageComparisonItem,
    PlatformComparisonItem,
)
from app.schemas.contract import PlatformEnum

logger = logging.getLogger("communityott.analytics.advanced")


class AnalyticsAdvancedService:
    """Coordinates upstream data retrieval and statistical/business analytics execution."""

    def __init__(
        self,
        client: Optional[AnalyticsClient] = None,
        settings: Optional[Settings] = None,
    ):
        self.settings = settings or get_settings()
        self.client = client or AnalyticsClient(settings=self.settings)

    async def _fetch_and_prepare_dataframe(
        self,
        from_date: Optional[date] = None,
        to_date: Optional[date] = None,
        platform: Optional[Union[PlatformEnum, str]] = None,
        content_id: Optional[int] = None,
        category_id: Optional[int] = None,
        language_id: Optional[int] = None,
        auth_token: Optional[str] = None,
    ) -> pd.DataFrame:
        """Fetch records from Spring Boot, convert to typed DataFrame, validate and clean."""
        records = await self.client.fetch_all(
            from_date=from_date,
            to_date=to_date,
            platform=platform,
            content_id=content_id,
            category_id=category_id,
            language_id=language_id,
            auth_token=auth_token,
        )
        df = build_dataframe(records)
        validate_dataframe(df)
        return clean_dataframe(df)

    async def get_engagement_analytics(
        self,
        from_date: Optional[date] = None,
        to_date: Optional[date] = None,
        platform: Optional[Union[PlatformEnum, str]] = None,
        content_id: Optional[int] = None,
        auth_token: Optional[str] = None,
    ) -> EngagementAnalyticsResponse:
        """Calculate holistic viewer engagement, completion ratios, and streaming quality metrics."""
        df = await self._fetch_and_prepare_dataframe(
            from_date=from_date,
            to_date=to_date,
            platform=platform,
            content_id=content_id,
            auth_token=auth_token,
        )
        metrics = calculate_engagement_metrics(df)
        return EngagementAnalyticsResponse(**metrics)

    async def get_content_performance(
        self,
        from_date: Optional[date] = None,
        to_date: Optional[date] = None,
        platform: Optional[Union[PlatformEnum, str]] = None,
        by: str = "performance_score",
        limit: int = 10,
        auth_token: Optional[str] = None,
    ) -> List[ContentPerformanceScoreItem]:
        """Rank and return top performing content assets with composite performance scores."""
        df = await self._fetch_and_prepare_dataframe(
            from_date=from_date,
            to_date=to_date,
            platform=platform,
            auth_token=auth_token,
        )
        ranked_df = calculate_content_performance_scores(df, by=by, limit=limit)
        if ranked_df.empty:
            return []

        results: List[ContentPerformanceScoreItem] = []
        for _, row in ranked_df.iterrows():
            results.append(
                ContentPerformanceScoreItem(
                    rank=int(row["rank"]),
                    content_id=int(row["content_id"]),
                    sessions=int(row["sessions"]),
                    plays=int(row["plays"]),
                    unique_viewers=int(row["unique_viewers"]),
                    watch_time_seconds=int(row["watch_time_seconds"]),
                    completed_plays=int(row["completed_plays"]),
                    completion_rate=float(row["completion_rate"]),
                    normalized_plays=float(row["normalized_plays"]),
                    normalized_watch_time=float(row["normalized_watch_time"]),
                    normalized_viewers=float(row["normalized_viewers"]),
                    performance_score=float(row["performance_score"]),
                )
            )
        return results

    async def get_growth_analysis(
        self,
        from_date: Optional[date] = None,
        to_date: Optional[date] = None,
        platform: Optional[Union[PlatformEnum, str]] = None,
        split_date: Optional[date] = None,
        auth_token: Optional[str] = None,
    ) -> GrowthAnalysisResponse:
        """Perform period-over-period growth comparisons across all operational metrics."""
        df = await self._fetch_and_prepare_dataframe(
            from_date=from_date,
            to_date=to_date,
            platform=platform,
            auth_token=auth_token,
        )
        raw_growth = calculate_period_growth(df, split_date=split_date)
        metrics_map = {
            m: GrowthMetricItem(
                current_value=data["current_value"],
                previous_value=data["previous_value"],
                growth_percentage=data["growth_percentage"],
                trend=data["trend"],
            )
            for m, data in raw_growth["metrics"].items()
        }
        return GrowthAnalysisResponse(
            current_period_start=raw_growth["current_period_start"],
            current_period_end=raw_growth["current_period_end"],
            previous_period_start=raw_growth["previous_period_start"],
            previous_period_end=raw_growth["previous_period_end"],
            metrics=metrics_map,
        )

    async def get_daily_trends(
        self,
        from_date: Optional[date] = None,
        to_date: Optional[date] = None,
        platform: Optional[Union[PlatformEnum, str]] = None,
        content_id: Optional[int] = None,
        auth_token: Optional[str] = None,
    ) -> List[DailyTrendItem]:
        """Generate chronological daily trend time-series with day-over-day growth tracking."""
        df = await self._fetch_and_prepare_dataframe(
            from_date=from_date,
            to_date=to_date,
            platform=platform,
            content_id=content_id,
            auth_token=auth_token,
        )
        raw_trends = calculate_daily_trends(df)
        return [DailyTrendItem(**item) for item in raw_trends]

    async def get_platform_comparison(
        self,
        from_date: Optional[date] = None,
        to_date: Optional[date] = None,
        content_id: Optional[int] = None,
        auth_token: Optional[str] = None,
    ) -> List[PlatformComparisonItem]:
        """Compare playback behavior across platforms with market share ratios."""
        df = await self._fetch_and_prepare_dataframe(
            from_date=from_date,
            to_date=to_date,
            content_id=content_id,
            auth_token=auth_token,
        )
        agg_df = aggregate_by_platform(df)
        if agg_df.empty:
            return []

        total_plays = float(agg_df["plays"].sum())
        total_watch = float(agg_df["watch_time_seconds"].sum())
        total_viewers = float(agg_df["unique_viewers"].sum())

        results: List[PlatformComparisonItem] = []
        for _, row in agg_df.iterrows():
            p_plays = float(row["plays"])
            p_watch = float(row["watch_time_seconds"])
            p_viewers = float(row["unique_viewers"])

            share_plays = p_plays / total_plays if total_plays > 0 else 0.0
            share_watch = p_watch / total_watch if total_watch > 0 else 0.0
            share_viewers = p_viewers / total_viewers if total_viewers > 0 else 0.0

            results.append(
                PlatformComparisonItem(
                    platform=str(row["platform"]),
                    sessions=int(row["sessions"]),
                    plays=int(row["plays"]),
                    unique_viewers=int(row["unique_viewers"]),
                    watch_time_seconds=int(row["watch_time_seconds"]),
                    completed_plays=int(row["completed_plays"]),
                    completion_rate=float(row["completion_rate"]),
                    buffering_events=int(row["buffering_events"]),
                    playback_errors=int(row["playback_errors"]),
                    quality_changes=int(row["quality_changes"]),
                    share_of_total_plays=round(share_plays, 4),
                    share_of_total_watch_time=round(share_watch, 4),
                    share_of_total_viewers=round(share_viewers, 4),
                )
            )
        return results

    async def get_category_comparison(
        self,
        from_date: Optional[date] = None,
        to_date: Optional[date] = None,
        platform: Optional[Union[PlatformEnum, str]] = None,
        auth_token: Optional[str] = None,
    ) -> List[CategoryComparisonItem]:
        """Compare playback and catalog distribution shares across categories."""
        df = await self._fetch_and_prepare_dataframe(
            from_date=from_date,
            to_date=to_date,
            platform=platform,
            auth_token=auth_token,
        )
        agg_df = aggregate_by_category(df)
        if agg_df.empty:
            return []

        total_plays = float(agg_df["plays"].sum())
        total_watch = float(agg_df["watch_time_seconds"].sum())

        results: List[CategoryComparisonItem] = []
        for _, row in agg_df.iterrows():
            cat_val = row["category_id"]
            cat_id = int(cat_val) if pd.notna(cat_val) else None
            cat_label = f"Category {cat_id}" if cat_id is not None else "UNASSIGNED"

            c_plays = float(row["plays"])
            c_watch = float(row["watch_time_seconds"])

            share_plays = c_plays / total_plays if total_plays > 0 else 0.0
            share_watch = c_watch / total_watch if total_watch > 0 else 0.0

            results.append(
                CategoryComparisonItem(
                    category_id=cat_id,
                    category_label=cat_label,
                    sessions=int(row["sessions"]),
                    plays=int(row["plays"]),
                    unique_viewers=int(row["unique_viewers"]),
                    watch_time_seconds=int(row["watch_time_seconds"]),
                    completed_plays=int(row["completed_plays"]),
                    completion_rate=float(row["completion_rate"]),
                    share_of_total_plays=round(share_plays, 4),
                    share_of_total_watch_time=round(share_watch, 4),
                )
            )
        return results

    async def get_language_comparison(
        self,
        from_date: Optional[date] = None,
        to_date: Optional[date] = None,
        platform: Optional[Union[PlatformEnum, str]] = None,
        auth_token: Optional[str] = None,
    ) -> List[LanguageComparisonItem]:
        """Compare playback and catalog distribution shares across languages."""
        df = await self._fetch_and_prepare_dataframe(
            from_date=from_date,
            to_date=to_date,
            platform=platform,
            auth_token=auth_token,
        )
        agg_df = aggregate_by_language(df)
        if agg_df.empty:
            return []

        total_plays = float(agg_df["plays"].sum())
        total_watch = float(agg_df["watch_time_seconds"].sum())

        results: List[LanguageComparisonItem] = []
        for _, row in agg_df.iterrows():
            lang_val = row["language_id"]
            lang_id = int(lang_val) if pd.notna(lang_val) else None
            lang_label = f"Language {lang_id}" if lang_id is not None else "UNASSIGNED"

            l_plays = float(row["plays"])
            l_watch = float(row["watch_time_seconds"])

            share_plays = l_plays / total_plays if total_plays > 0 else 0.0
            share_watch = l_watch / total_watch if total_watch > 0 else 0.0

            results.append(
                LanguageComparisonItem(
                    language_id=lang_id,
                    language_label=lang_label,
                    sessions=int(row["sessions"]),
                    plays=int(row["plays"]),
                    unique_viewers=int(row["unique_viewers"]),
                    watch_time_seconds=int(row["watch_time_seconds"]),
                    completed_plays=int(row["completed_plays"]),
                    completion_rate=float(row["completion_rate"]),
                    share_of_total_plays=round(share_plays, 4),
                    share_of_total_watch_time=round(share_watch, 4),
                )
            )
        return results

    async def get_distribution_analysis(
        self,
        from_date: Optional[date] = None,
        to_date: Optional[date] = None,
        platform: Optional[Union[PlatformEnum, str]] = None,
        auth_token: Optional[str] = None,
    ) -> DistributionAnalysisResponse:
        """Compute statistical distributions and percentiles (P25 to P95) for primary metrics."""
        df = await self._fetch_and_prepare_dataframe(
            from_date=from_date,
            to_date=to_date,
            platform=platform,
            auth_token=auth_token,
        )
        raw_dist = calculate_dataset_distributions(df)
        return DistributionAnalysisResponse(
            plays=DistributionMetricItem(**raw_dist["plays"]),
            watch_time_seconds=DistributionMetricItem(**raw_dist["watch_time_seconds"]),
            completion_rate=DistributionMetricItem(**raw_dist["completion_rate"]),
            unique_viewers=DistributionMetricItem(**raw_dist["unique_viewers"]),
        )

    async def get_anomalies(
        self,
        from_date: Optional[date] = None,
        to_date: Optional[date] = None,
        platform: Optional[Union[PlatformEnum, str]] = None,
        auth_token: Optional[str] = None,
    ) -> AnomalyDetectionResponse:
        """Identify statistical outliers using the Interquartile Range (IQR) method."""
        df = await self._fetch_and_prepare_dataframe(
            from_date=from_date,
            to_date=to_date,
            platform=platform,
            auth_token=auth_token,
        )
        raw_anomalies = detect_anomalies_iqr(df)
        anomaly_items = [AnomalyItem(**a) for a in raw_anomalies]
        return AnomalyDetectionResponse(
            method="IQR",
            total_anomalies=len(anomaly_items),
            anomalies=anomaly_items,
        )

    async def get_insights(
        self,
        from_date: Optional[date] = None,
        to_date: Optional[date] = None,
        platform: Optional[Union[PlatformEnum, str]] = None,
        auth_token: Optional[str] = None,
    ) -> InsightsResponse:
        """Evaluate deterministic business health heuristics across content and platform."""
        df = await self._fetch_and_prepare_dataframe(
            from_date=from_date,
            to_date=to_date,
            platform=platform,
            auth_token=auth_token,
        )
        raw_insights = evaluate_content_insights(df, settings=self.settings)
        insight_items = [InsightItem(**item) for item in raw_insights]
        return InsightsResponse(
            total_insights=len(insight_items),
            insights=insight_items,
        )
