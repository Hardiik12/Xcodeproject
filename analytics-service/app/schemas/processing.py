"""Pydantic response models for analytics processing endpoints."""

from datetime import date
from typing import Dict, List, Optional
from pydantic import BaseModel, ConfigDict, Field


class MetricStats(BaseModel):
    """Statistical summary metrics."""
    model_config = ConfigDict(populate_by_name=True)

    count: int = Field(ge=0, description="Sample count")
    mean: float = Field(description="Arithmetic mean")
    median: float = Field(description="50th percentile (median)")
    minimum: float = Field(description="Minimum value")
    maximum: float = Field(description="Maximum value")
    standard_deviation: float = Field(description="Sample standard deviation (ddof=1)")


class ProcessingSummaryResponse(BaseModel):
    """Overall dataset summary and statistical breakdown."""
    model_config = ConfigDict(populate_by_name=True)

    contract_version: str = Field(description="analytics-contract-v1")
    total_records: int = Field(ge=0, description="Total ingested daily records")
    distinct_contents: int = Field(ge=0, description="Count of distinct content assets")
    statistics: Dict[str, MetricStats] = Field(description="Statistical distributions for metrics")


class ContentPerformanceItem(BaseModel):
    """Aggregated performance metrics for a single content item."""
    model_config = ConfigDict(populate_by_name=True)

    content_id: int = Field(ge=1)
    sessions: int = Field(ge=0)
    plays: int = Field(ge=0)
    unique_viewers: int = Field(ge=0)
    watch_time_seconds: int = Field(ge=0)
    completed_plays: int = Field(ge=0)
    completion_rate: float = Field(ge=0.0, le=1.0)
    buffering_events: int = Field(ge=0)
    playback_errors: int = Field(ge=0)
    quality_changes: int = Field(ge=0)


class PlatformAggregatedItem(BaseModel):
    """Aggregated metrics grouped by client platform."""
    model_config = ConfigDict(populate_by_name=True)

    platform: str
    sessions: int = Field(ge=0)
    plays: int = Field(ge=0)
    unique_viewers: int = Field(ge=0)
    watch_time_seconds: int = Field(ge=0)
    completed_plays: int = Field(ge=0)
    completion_rate: float = Field(ge=0.0, le=1.0)
    buffering_events: int = Field(ge=0)
    playback_errors: int = Field(ge=0)
    quality_changes: int = Field(ge=0)


class CategoryAggregatedItem(BaseModel):
    """Aggregated metrics grouped by content category."""
    model_config = ConfigDict(populate_by_name=True)

    category_id: Optional[int] = Field(default=None)
    sessions: int = Field(ge=0)
    plays: int = Field(ge=0)
    unique_viewers: int = Field(ge=0)
    watch_time_seconds: int = Field(ge=0)
    completed_plays: int = Field(ge=0)
    completion_rate: float = Field(ge=0.0, le=1.0)
    buffering_events: int = Field(ge=0)
    playback_errors: int = Field(ge=0)
    quality_changes: int = Field(ge=0)


class LanguageAggregatedItem(BaseModel):
    """Aggregated metrics grouped by content language."""
    model_config = ConfigDict(populate_by_name=True)

    language_id: Optional[int] = Field(default=None)
    sessions: int = Field(ge=0)
    plays: int = Field(ge=0)
    unique_viewers: int = Field(ge=0)
    watch_time_seconds: int = Field(ge=0)
    completed_plays: int = Field(ge=0)
    completion_rate: float = Field(ge=0.0, le=1.0)
    buffering_events: int = Field(ge=0)
    playback_errors: int = Field(ge=0)
    quality_changes: int = Field(ge=0)
