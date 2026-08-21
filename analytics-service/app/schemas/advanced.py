"""Pydantic schemas for Phase 7.4 Advanced Statistical and Business Analytics."""

from typing import Dict, List, Optional
from pydantic import BaseModel, ConfigDict, Field


class EngagementAnalyticsResponse(BaseModel):
    """Holistic engagement analytics with derived ratios and rates."""

    model_config = ConfigDict(extra="forbid")

    total_sessions: int = Field(description="Aggregate user sessions across dataset")
    total_plays: int = Field(description="Aggregate content playback attempts")
    total_unique_viewers: int = Field(description="Summed daily unique viewers")
    total_watch_time_seconds: int = Field(description="Aggregate total watch duration in seconds")
    total_completed_plays: int = Field(description="Aggregate completed video plays")
    overall_completion_rate: float = Field(description="Ratio of total completed plays over total plays")
    average_watch_time_per_play: float = Field(description="Mean watch duration per play in seconds")
    average_watch_time_per_session: float = Field(description="Mean watch duration per session in seconds")
    plays_per_viewer: float = Field(description="Average playback attempts per unique viewer")
    sessions_per_viewer: float = Field(description="Average sessions per unique viewer")
    completion_ratio: float = Field(description="Deterministic completion ratio (completed_plays / plays)")
    buffering_rate: float = Field(description="Buffer events per play")
    playback_error_rate: float = Field(description="Playback errors per play")
    quality_change_rate: float = Field(description="Quality rendition switches per play")


class ContentPerformanceScoreItem(BaseModel):
    """Detailed content performance record with normalized components and deterministic score."""

    model_config = ConfigDict(extra="forbid")

    rank: int = Field(description="1-based performance rank")
    content_id: int = Field(description="Unique content asset identifier")
    sessions: int = Field(description="Total sessions")
    plays: int = Field(description="Total plays")
    unique_viewers: int = Field(description="Summed unique viewers")
    watch_time_seconds: int = Field(description="Total watch duration in seconds")
    completed_plays: int = Field(description="Total completed plays")
    completion_rate: float = Field(description="Recalculated completion rate")
    normalized_plays: float = Field(description="Min-max normalized plays component in range [0.0, 1.0]")
    normalized_watch_time: float = Field(description="Min-max normalized watch time in range [0.0, 1.0]")
    normalized_viewers: float = Field(description="Min-max normalized viewers in range [0.0, 1.0]")
    performance_score: float = Field(description="Composite business performance score in range [0.0, 100.0]")


class GrowthMetricItem(BaseModel):
    """Period-over-period growth comparison for a specific business metric."""

    model_config = ConfigDict(extra="forbid")

    current_value: float = Field(description="Metric value in the current period")
    previous_value: float = Field(description="Metric value in the previous comparison period")
    growth_percentage: float = Field(description="Percentage change ((current - previous) / previous) * 100")
    trend: str = Field(description="Trend direction: UP, DOWN, or FLAT")


class GrowthAnalysisResponse(BaseModel):
    """Period-over-period growth analytics across primary operational metrics."""

    model_config = ConfigDict(extra="forbid")

    current_period_start: str = Field(description="Start date of current period (YYYY-MM-DD)")
    current_period_end: str = Field(description="End date of current period (YYYY-MM-DD)")
    previous_period_start: str = Field(description="Start date of previous period (YYYY-MM-DD)")
    previous_period_end: str = Field(description="End date of previous period (YYYY-MM-DD)")
    metrics: Dict[str, GrowthMetricItem] = Field(description="Growth calculation per metric")


class DailyTrendItem(BaseModel):
    """Daily time-series aggregation item with day-over-day growth tracking."""

    model_config = ConfigDict(extra="forbid")

    metric_date: str = Field(description="Metric calendar date (YYYY-MM-DD) in UTC")
    sessions: int = Field(description="Total daily sessions")
    plays: int = Field(description="Total daily plays")
    unique_viewers: int = Field(description="Total daily unique viewers")
    watch_time_seconds: int = Field(description="Total daily watch time in seconds")
    completed_plays: int = Field(description="Total daily completed plays")
    completion_rate: float = Field(description="Daily recalculated completion rate")
    day_over_day_plays_growth: Optional[float] = Field(default=None, description="Percentage change in plays vs prior day")
    day_over_day_watch_time_growth: Optional[float] = Field(default=None, description="Percentage change in watch time vs prior day")
    day_over_day_viewers_growth: Optional[float] = Field(default=None, description="Percentage change in viewers vs prior day")


class PlatformComparisonItem(BaseModel):
    """Platform metrics comparison with platform market share breakdown."""

    model_config = ConfigDict(extra="forbid")

    platform: str = Field(description="Client playback platform (IOS, ANDROID, WEB)")
    sessions: int = Field(description="Total sessions on platform")
    plays: int = Field(description="Total plays on platform")
    unique_viewers: int = Field(description="Total unique viewers on platform")
    watch_time_seconds: int = Field(description="Total watch duration on platform")
    completed_plays: int = Field(description="Total completed plays on platform")
    completion_rate: float = Field(description="Recalculated completion rate on platform")
    buffering_events: int = Field(description="Total buffering events on platform")
    playback_errors: int = Field(description="Total playback errors on platform")
    quality_changes: int = Field(description="Total quality changes on platform")
    share_of_total_plays: float = Field(description="Platform share of aggregate plays (0.0 to 1.0)")
    share_of_total_watch_time: float = Field(description="Platform share of aggregate watch time (0.0 to 1.0)")
    share_of_total_viewers: float = Field(description="Platform share of aggregate viewers (0.0 to 1.0)")


class CategoryComparisonItem(BaseModel):
    """Category metrics comparison with catalog distribution share."""

    model_config = ConfigDict(extra="forbid")

    category_id: Optional[int] = Field(default=None, description="Category identifier (null for unassigned)")
    category_label: str = Field(description="Category label ('UNASSIGNED' or ID)")
    sessions: int = Field(description="Total sessions in category")
    plays: int = Field(description="Total plays in category")
    unique_viewers: int = Field(description="Total unique viewers in category")
    watch_time_seconds: int = Field(description="Total watch duration in category")
    completed_plays: int = Field(description="Total completed plays in category")
    completion_rate: float = Field(description="Recalculated completion rate in category")
    share_of_total_plays: float = Field(description="Category share of aggregate plays (0.0 to 1.0)")
    share_of_total_watch_time: float = Field(description="Category share of aggregate watch time (0.0 to 1.0)")


class LanguageComparisonItem(BaseModel):
    """Language metrics comparison with catalog distribution share."""

    model_config = ConfigDict(extra="forbid")

    language_id: Optional[int] = Field(default=None, description="Language identifier (null for unassigned)")
    language_label: str = Field(description="Language label ('UNASSIGNED' or ID)")
    sessions: int = Field(description="Total sessions in language")
    plays: int = Field(description="Total plays in language")
    unique_viewers: int = Field(description="Total unique viewers in language")
    watch_time_seconds: int = Field(description="Total watch duration in language")
    completed_plays: int = Field(description="Total completed plays in language")
    completion_rate: float = Field(description="Recalculated completion rate in language")
    share_of_total_plays: float = Field(description="Language share of aggregate plays (0.0 to 1.0)")
    share_of_total_watch_time: float = Field(description="Language share of aggregate watch time (0.0 to 1.0)")


class DistributionMetricItem(BaseModel):
    """Comprehensive statistical distribution metrics and percentiles for a numeric dimension."""

    model_config = ConfigDict(extra="forbid")

    count: int = Field(description="Sample size / number of observations")
    mean: float = Field(description="Arithmetic mean")
    median: float = Field(description="Median / 50th percentile")
    minimum: float = Field(description="Minimum observation")
    maximum: float = Field(description="Maximum observation")
    standard_deviation: float = Field(description="Sample standard deviation (ddof=1)")
    p25: float = Field(description="25th percentile (Q1)")
    p50: float = Field(description="50th percentile (Median / Q2)")
    p75: float = Field(description="75th percentile (Q3)")
    p90: float = Field(description="90th percentile")
    p95: float = Field(description="95th percentile")


class DistributionAnalysisResponse(BaseModel):
    """Distribution summaries across all key continuous analytics metrics."""

    model_config = ConfigDict(extra="forbid")

    plays: DistributionMetricItem
    watch_time_seconds: DistributionMetricItem
    completion_rate: DistributionMetricItem
    unique_viewers: DistributionMetricItem


class AnomalyItem(BaseModel):
    """Statistical outlier record detected using Interquartile Range (IQR) bounds."""

    model_config = ConfigDict(extra="forbid")

    date: str = Field(description="Observation date (YYYY-MM-DD)")
    metric: str = Field(description="Analyzed metric name")
    value: float = Field(description="Observed metric value")
    lower_bound: float = Field(description="Calculated statistical lower bound (Q1 - 1.5*IQR)")
    upper_bound: float = Field(description="Calculated statistical upper bound (Q3 + 1.5*IQR)")
    severity: str = Field(description="Severity classification: LOW, MEDIUM, or HIGH")


class AnomalyDetectionResponse(BaseModel):
    """Collection of detected statistical anomalies."""

    model_config = ConfigDict(extra="forbid")

    method: str = Field(default="IQR", description="Deterministic statistical outlier detection method used")
    total_anomalies: int = Field(description="Number of detected anomaly items")
    anomalies: List[AnomalyItem] = Field(description="List of detected statistical outliers")


class InsightItem(BaseModel):
    """Actionable heuristic business health insight for content or platform."""

    model_config = ConfigDict(extra="forbid")

    type: str = Field(description="Insight rule type (e.g., HIGH_ENGAGEMENT, LOW_COMPLETION, HIGH_BUFFERING)")
    severity: str = Field(description="Severity indicator: LOW, MEDIUM, HIGH")
    content_id: Optional[int] = Field(default=None, description="Related content asset ID if content-specific")
    metric: str = Field(description="Triggering metric name")
    value: float = Field(description="Current observed metric value")
    threshold: float = Field(description="Configured business heuristic threshold")
    message: str = Field(description="Human-readable explanatory summary")


class InsightsResponse(BaseModel):
    """Collection of evaluated content and operational insights."""

    model_config = ConfigDict(extra="forbid")

    total_insights: int = Field(description="Total count of triggered business insights")
    insights: List[InsightItem] = Field(description="List of structured health insights")
