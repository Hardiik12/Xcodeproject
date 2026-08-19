"""Strongly-typed Pydantic schemas validating analytics-contract-v1 from Spring Boot."""

import datetime as dt
from enum import Enum
from typing import List, Optional
from pydantic import BaseModel, Field, field_validator, ConfigDict


class PlatformEnum(str, Enum):
    """Supported client platforms."""
    IOS = "IOS"
    ANDROID = "ANDROID"
    WEB = "WEB"


class AnalyticsExportRecord(BaseModel):
    """Single aggregated content daily performance record adhering to analytics-contract-v1."""

    model_config = ConfigDict(populate_by_name=True)

    date: dt.date = Field(description="Metric date in ISO-8601 YYYY-MM-DD")
    content_id: int = Field(ge=1, description="Unique identifier of the content asset")
    category_id: Optional[int] = Field(default=None, ge=1, description="Primary category ID if assigned")
    language_id: Optional[int] = Field(default=None, ge=1, description="Original language ID if assigned")
    platform: PlatformEnum = Field(description="Client playback platform (IOS, ANDROID, WEB)")
    sessions: int = Field(ge=0, description="Total playback session initiations")
    plays: int = Field(ge=0, description="Total play events")
    unique_viewers: int = Field(ge=0, description="Count of distinct viewers")
    watch_time_seconds: int = Field(ge=0, description="Total cumulative watch duration in seconds")
    completed_plays: int = Field(ge=0, description="Total playback sessions completed")
    completion_rate: float = Field(
        ge=0.0,
        le=1.0,
        description="Ratio of completed plays to total plays (0.0 to 1.0, 4 decimal places)",
    )
    buffering_events: int = Field(ge=0, description="Count of buffering stalls")
    playback_errors: int = Field(ge=0, description="Count of fatal or non-fatal playback errors")
    quality_changes: int = Field(ge=0, description="Count of adaptive bitrate rendition changes")

    @field_validator("platform", mode="before")
    @classmethod
    def validate_platform_case(cls, v: str) -> str:
        """Allow case-insensitive platform values."""
        if isinstance(v, str):
            v_upper = v.upper()
            if v_upper in PlatformEnum.__members__:
                return v_upper
        return v


class AnalyticsExportResponse(BaseModel):
    """Envelope response for GET /api/v1/analytics/export conforming to analytics-contract-v1."""

    model_config = ConfigDict(populate_by_name=True)

    contract_version: str = Field(
        description="Agreed schema version string (must equal 'analytics-contract-v1')"
    )
    generated_at: dt.datetime = Field(
        description="UTC timestamp when the export payload was generated"
    )
    from_date: dt.date = Field(
        alias="from",
        description="Start date of the export interval (inclusive)",
    )
    to_date: dt.date = Field(
        alias="to",
        description="End date of the export interval (inclusive)",
    )
    page: int = Field(ge=0, description="Current page index (0-indexed)")
    size: int = Field(ge=1, le=100, description="Number of records per page (max 100)")
    total_records: int = Field(ge=0, description="Total matching record count across all pages")
    total_pages: int = Field(ge=0, description="Total number of pages available")
    has_next: bool = Field(description="True if additional pages remain")
    records: List[AnalyticsExportRecord] = Field(
        default_factory=list,
        description="List of aggregated daily metric records",
    )
