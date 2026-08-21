"""Service layer coordinating client ingestion, DataFrame building, validation, and analytics processing."""

import logging
from datetime import date
from typing import List, Optional, Union
import pandas as pd

from app.clients.analytics_client import AnalyticsClient
from app.core.config import Settings, get_settings
from app.processing.aggregations import (
    aggregate_by_category,
    aggregate_by_content,
    aggregate_by_language,
    aggregate_by_platform,
)
from app.processing.data_cleaner import clean_dataframe
from app.processing.data_validator import validate_dataframe
from app.processing.dataframe_builder import build_dataframe
from app.processing.statistics import (
    calculate_metric_statistics,
    get_top_content,
)
from app.schemas.contract import PlatformEnum
from app.schemas.processing import (
    CategoryAggregatedItem,
    ContentPerformanceItem,
    LanguageAggregatedItem,
    MetricStats,
    PlatformAggregatedItem,
    ProcessingSummaryResponse,
)

logger = logging.getLogger("communityott.analytics.service")


class AnalyticsProcessingService:
    """Orchestrates dataset retrieval from Spring Boot and deterministic processing with Pandas & NumPy."""

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
        """Fetch records from Spring Boot export, construct DataFrame, and enforce validation rules."""
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
        df_cleaned = clean_dataframe(df)
        return df_cleaned

    async def get_summary(
        self,
        from_date: Optional[date] = None,
        to_date: Optional[date] = None,
        platform: Optional[Union[PlatformEnum, str]] = None,
        content_id: Optional[int] = None,
        category_id: Optional[int] = None,
        language_id: Optional[int] = None,
        auth_token: Optional[str] = None,
    ) -> ProcessingSummaryResponse:
        """Compute holistic dataset summary and statistical distributions."""
        df = await self._fetch_and_prepare_dataframe(
            from_date=from_date,
            to_date=to_date,
            platform=platform,
            content_id=content_id,
            category_id=category_id,
            language_id=language_id,
            auth_token=auth_token,
        )

        total_records = len(df)
        distinct_contents = int(df["content_id"].nunique()) if not df.empty else 0
        raw_stats = calculate_metric_statistics(df)

        stats_dict = {
            metric: MetricStats(
                count=data["count"],
                mean=data["mean"],
                median=data["median"],
                minimum=data["minimum"],
                maximum=data["maximum"],
                standard_deviation=data["standard_deviation"],
            )
            for metric, data in raw_stats.items()
        }

        return ProcessingSummaryResponse(
            contract_version=self.settings.ANALYTICS_CONTRACT_VERSION,
            total_records=total_records,
            distinct_contents=distinct_contents,
            statistics=stats_dict,
        )

    async def get_content_performance(
        self,
        from_date: Optional[date] = None,
        to_date: Optional[date] = None,
        platform: Optional[Union[PlatformEnum, str]] = None,
        by: str = "plays",
        limit: int = 10,
        auth_token: Optional[str] = None,
    ) -> List[ContentPerformanceItem]:
        """Rank and return top performing content assets."""
        df = await self._fetch_and_prepare_dataframe(
            from_date=from_date,
            to_date=to_date,
            platform=platform,
            auth_token=auth_token,
        )

        ranked_df = get_top_content(df, by=by, limit=limit)
        if ranked_df.empty:
            return []

        results: List[ContentPerformanceItem] = []
        for _, row in ranked_df.iterrows():
            results.append(
                ContentPerformanceItem(
                    content_id=int(row["content_id"]),
                    sessions=int(row["sessions"]),
                    plays=int(row["plays"]),
                    unique_viewers=int(row["unique_viewers"]),
                    watch_time_seconds=int(row["watch_time_seconds"]),
                    completed_plays=int(row["completed_plays"]),
                    completion_rate=float(row["completion_rate"]),
                    buffering_events=int(row["buffering_events"]),
                    playback_errors=int(row["playback_errors"]),
                    quality_changes=int(row["quality_changes"]),
                )
            )
        return results

    async def get_platform_breakdown(
        self,
        from_date: Optional[date] = None,
        to_date: Optional[date] = None,
        content_id: Optional[int] = None,
        auth_token: Optional[str] = None,
    ) -> List[PlatformAggregatedItem]:
        """Aggregate metric distributions grouped across client platforms."""
        df = await self._fetch_and_prepare_dataframe(
            from_date=from_date,
            to_date=to_date,
            content_id=content_id,
            auth_token=auth_token,
        )

        agg_df = aggregate_by_platform(df)
        if agg_df.empty:
            return []

        results: List[PlatformAggregatedItem] = []
        for _, row in agg_df.iterrows():
            results.append(
                PlatformAggregatedItem(
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
                )
            )
        return results

    async def get_category_breakdown(
        self,
        from_date: Optional[date] = None,
        to_date: Optional[date] = None,
        platform: Optional[Union[PlatformEnum, str]] = None,
        auth_token: Optional[str] = None,
    ) -> List[CategoryAggregatedItem]:
        """Aggregate metric distributions grouped across content categories."""
        df = await self._fetch_and_prepare_dataframe(
            from_date=from_date,
            to_date=to_date,
            platform=platform,
            auth_token=auth_token,
        )

        agg_df = aggregate_by_category(df)
        if agg_df.empty:
            return []

        results: List[CategoryAggregatedItem] = []
        for _, row in agg_df.iterrows():
            cat_val = row["category_id"]
            cat_id = int(cat_val) if pd.notna(cat_val) else None
            results.append(
                CategoryAggregatedItem(
                    category_id=cat_id,
                    sessions=int(row["sessions"]),
                    plays=int(row["plays"]),
                    unique_viewers=int(row["unique_viewers"]),
                    watch_time_seconds=int(row["watch_time_seconds"]),
                    completed_plays=int(row["completed_plays"]),
                    completion_rate=float(row["completion_rate"]),
                    buffering_events=int(row["buffering_events"]),
                    playback_errors=int(row["playback_errors"]),
                    quality_changes=int(row["quality_changes"]),
                )
            )
        return results

    async def get_language_breakdown(
        self,
        from_date: Optional[date] = None,
        to_date: Optional[date] = None,
        platform: Optional[Union[PlatformEnum, str]] = None,
        auth_token: Optional[str] = None,
    ) -> List[LanguageAggregatedItem]:
        """Aggregate metric distributions grouped across content languages."""
        df = await self._fetch_and_prepare_dataframe(
            from_date=from_date,
            to_date=to_date,
            platform=platform,
            auth_token=auth_token,
        )

        agg_df = aggregate_by_language(df)
        if agg_df.empty:
            return []

        results: List[LanguageAggregatedItem] = []
        for _, row in agg_df.iterrows():
            lang_val = row["language_id"]
            lang_id = int(lang_val) if pd.notna(lang_val) else None
            results.append(
                LanguageAggregatedItem(
                    language_id=lang_id,
                    sessions=int(row["sessions"]),
                    plays=int(row["plays"]),
                    unique_viewers=int(row["unique_viewers"]),
                    watch_time_seconds=int(row["watch_time_seconds"]),
                    completed_plays=int(row["completed_plays"]),
                    completion_rate=float(row["completion_rate"]),
                    buffering_events=int(row["buffering_events"]),
                    playback_errors=int(row["playback_errors"]),
                    quality_changes=int(row["quality_changes"]),
                )
            )
        return results
