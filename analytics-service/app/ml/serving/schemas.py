"""Pydantic schemas for ML serving, predictions, health, readiness, and metrics."""

from datetime import date as DateType, datetime, timezone
import math
from typing import Any, Dict, List, Optional
from pydantic import BaseModel, Field, field_validator, model_validator

FORBIDDEN_TARGET_COLUMNS = {
    "target_next_day_plays",
    "target_next_day_watch_time",
    "target_next_day_completion_rate",
}

FORBIDDEN_PII_FIELDS = {
    "user_id",
    "email",
    "phone",
    "ip_address",
    "device_id",
    "session_id",
    "authorization",
    "auth_token",
    "password",
}


class PredictionInputRecord(BaseModel):
    """Single input observation payload for prediction."""

    model_config = {"extra": "allow"}

    content_id: int = Field(..., description="Target content ID", json_schema_extra={"example": 101})
    date: DateType = Field(..., description="Current observation UTC date (YYYY-MM-DD)", json_schema_extra={"example": "2026-08-15"})
    platform: str = Field(..., description="Client platform", json_schema_extra={"example": "IOS"})
    category_id: Optional[int] = Field(1, description="Content category ID")
    language_id: Optional[int] = Field(1, description="Content language ID")
    sessions: Optional[float] = Field(0.0, description="Session count")
    plays: Optional[float] = Field(0.0, description="Current day plays count")
    unique_viewers: Optional[float] = Field(0.0, description="Unique viewers count")
    watch_time_seconds: Optional[float] = Field(0.0, description="Watch time in seconds")
    completed_plays: Optional[float] = Field(0.0, description="Completed plays count")
    completion_rate: Optional[float] = Field(0.0, description="Completion rate ratio")
    buffering_events: Optional[float] = Field(0.0, description="Buffering event count")
    playback_errors: Optional[float] = Field(0.0, description="Playback error count")
    quality_changes: Optional[float] = Field(0.0, description="Quality change count")
    plays_lag_1d: Optional[float] = Field(None, description="1-day lag plays")
    sessions_lag_1d: Optional[float] = Field(None, description="1-day lag sessions")
    watch_time_lag_1d: Optional[float] = Field(None, description="1-day lag watch time")
    viewers_lag_1d: Optional[float] = Field(None, description="1-day lag viewers")
    completion_rate_lag_1d: Optional[float] = Field(None, description="1-day lag completion rate")
    plays_rolling_mean_7d: Optional[float] = Field(None, description="7-day rolling mean plays")
    plays_rolling_std_7d: Optional[float] = Field(None, description="7-day rolling std plays")
    watch_time_rolling_mean_7d: Optional[float] = Field(None, description="7-day rolling mean watch time")
    plays_growth_1d: Optional[float] = Field(None, description="1-day plays growth ratio")
    watch_time_growth_1d: Optional[float] = Field(None, description="1-day watch time growth ratio")
    viewer_growth_1d: Optional[float] = Field(None, description="1-day viewer growth ratio")

    @model_validator(mode="before")
    @classmethod
    def check_target_and_pii(cls, data: Any) -> Any:
        if isinstance(data, dict):
            for k in data.keys():
                if k.lower() in FORBIDDEN_TARGET_COLUMNS:
                    raise ValueError(f"Target column '{k}' is forbidden in prediction payload (TARGET_COLUMN_PRESENT).")
                if k.lower() in FORBIDDEN_PII_FIELDS:
                    raise ValueError(f"PII field '{k}' is forbidden in prediction payload (PII_SUPPLIED).")
        return data

    @field_validator(
        "sessions",
        "plays",
        "unique_viewers",
        "watch_time_seconds",
        "completed_plays",
        "buffering_events",
        "playback_errors",
        "quality_changes",
        "plays_lag_1d",
        "sessions_lag_1d",
        "watch_time_lag_1d",
        "viewers_lag_1d",
    )
    @classmethod
    def validate_non_negative_finite(cls, v: Optional[float], info: Any) -> Optional[float]:
        if v is not None:
            if math.isnan(v) or math.isinf(v):
                raise ValueError(f"Value for {info.field_name} must be finite (NaN/Inf forbidden).")
            if v < 0:
                raise ValueError(f"Value for {info.field_name} must be non-negative (got {v}).")
        return v

    @field_validator("completion_rate", "completion_rate_lag_1d")
    @classmethod
    def validate_completion_rate(cls, v: Optional[float], info: Any) -> Optional[float]:
        if v is not None:
            if math.isnan(v) or math.isinf(v):
                raise ValueError(f"Value for {info.field_name} must be finite.")
            if not (0.0 <= v <= 1.0):
                raise ValueError(f"Completion rate {info.field_name} must be between 0.0 and 1.0 (got {v}).")
        return v


class PredictionRequest(BaseModel):
    """Batch prediction request payload."""

    records: List[PredictionInputRecord] = Field(..., description="List of observation records (max 100)")

    @field_validator("records")
    @classmethod
    def validate_batch_size(cls, v: List[PredictionInputRecord]) -> List[PredictionInputRecord]:
        if not v:
            raise ValueError("Prediction request must contain at least 1 record.")
        if len(v) > 100:
            raise ValueError(f"Prediction batch size ({len(v)}) exceeds maximum allowable limit of 100 records.")
        return v


class PredictionResultItem(BaseModel):
    """Prediction result for a single record."""

    content_id: int
    date: DateType
    platform: str
    predicted_next_day_plays: float


class PredictionResponse(BaseModel):
    """Batch prediction response payload."""

    success: bool = True
    model_name: str
    model_version: str
    algorithm: str
    target: str = "target_next_day_plays"
    prediction_count: int
    predictions: List[PredictionResultItem]
    request_id: str


class StructuredErrorDetail(BaseModel):
    """Structured error detail payload."""

    code: str
    message: str
    request_id: str


class StructuredErrorResponse(BaseModel):
    """Standardized error envelope for API failures."""

    success: bool = False
    error: StructuredErrorDetail
    timestamp: str = Field(default_factory=lambda: datetime.now(timezone.utc).isoformat())


class ReadinessResponse(BaseModel):
    """Readiness probe status response."""

    status: str = Field(..., json_schema_extra={"example": "READY"})
    model: Optional[str] = Field(None, json_schema_extra={"example": "plays_predictor"})
    model_version: Optional[str] = Field(None, json_schema_extra={"example": "plays-predictor-v1"})
    reason: Optional[str] = Field(None, json_schema_extra={"example": None})


class ModelStatusResponse(BaseModel):
    """Model operational status metadata payload."""

    model_name: str
    model_version: str
    algorithm: str
    target: str
    feature_schema_version: str
    contract_version: str
    status: str
    validation_mae: Optional[float] = None
    validation_rmse: Optional[float] = None
    validation_r2: Optional[float] = None
