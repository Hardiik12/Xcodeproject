"""Data validation functions for analytics DataFrames."""

from typing import Set
import numpy as np
import pandas as pd

from app.processing.errors import DataValidationError, SensitiveDataError

REQUIRED_COLUMNS = [
    "date",
    "content_id",
    "category_id",
    "language_id",
    "platform",
    "sessions",
    "plays",
    "unique_viewers",
    "watch_time_seconds",
    "completed_plays",
    "completion_rate",
    "buffering_events",
    "playback_errors",
    "quality_changes",
]

SENSITIVE_COLUMNS: Set[str] = {
    "user_id",
    "email",
    "phone",
    "name",
    "first_name",
    "last_name",
    "username",
    "ip_address",
    "device_id",
    "session_id",
    "token",
    "password",
    "otp",
    "user_agent",
}

VALID_PLATFORMS: Set[str] = {"IOS", "ANDROID", "WEB"}

NUMERIC_METRIC_COLUMNS = [
    "sessions",
    "plays",
    "unique_viewers",
    "watch_time_seconds",
    "completed_plays",
    "buffering_events",
    "playback_errors",
    "quality_changes",
]


def validate_dataframe(df: pd.DataFrame) -> None:
    """Strictly validate an analytics DataFrame against contract invariants and privacy boundaries.

    Raises:
        SensitiveDataError: If any prohibited PII columns are detected.
        DataValidationError: If structure, types, non-negativity, or invariant checks fail.
    """
    if df is None:
        raise DataValidationError("DataFrame cannot be None")

    # 1. Check for sensitive PII columns
    found_sensitive = set(c.lower() for c in df.columns).intersection(SENSITIVE_COLUMNS)
    if found_sensitive:
        raise SensitiveDataError(
            f"Sensitive PII columns detected in analytics dataset: {list(found_sensitive)}. "
            "Analytics layer only accepts anonymized aggregate data."
        )

    # 2. Check required columns
    missing_cols = [col for col in REQUIRED_COLUMNS if col not in df.columns]
    if missing_cols:
        raise DataValidationError(f"Missing required columns in DataFrame: {missing_cols}")

    if df.empty:
        return

    # 3. Check for infinite values across all numeric columns
    numeric_cols = df.select_dtypes(include=[np.number]).columns
    for col in numeric_cols:
        if np.isinf(df[col]).any():
            raise DataValidationError(f"Infinite value detected in numeric column '{col}'")

    # 4. Check for NaN in required non-nullable fields
    non_nullable = [
        "date",
        "content_id",
        "platform",
        "sessions",
        "plays",
        "unique_viewers",
        "watch_time_seconds",
        "completed_plays",
        "completion_rate",
        "buffering_events",
        "playback_errors",
        "quality_changes",
    ]
    for col in non_nullable:
        if df[col].isna().any():
            raise DataValidationError(f"Null or NaN values detected in non-nullable column '{col}'")

    # 5. Check content_id validity
    if (df["content_id"] <= 0).any():
        raise DataValidationError("content_id must be a positive integer (>= 1)")

    # 6. Check platform values
    invalid_platforms = set(df["platform"].unique()) - VALID_PLATFORMS
    if invalid_platforms:
        raise DataValidationError(f"Invalid platform values detected: {invalid_platforms}. Allowed: {VALID_PLATFORMS}")

    # 7. Check non-negative constraints on metric columns
    for col in NUMERIC_METRIC_COLUMNS:
        if (df[col] < 0).any():
            raise DataValidationError(f"Negative values detected in metric column '{col}'")

    # 8. Check completion_rate bounds [0.0, 1.0]
    if ((df["completion_rate"] < 0.0) | (df["completion_rate"] > 1.0)).any():
        raise DataValidationError("completion_rate must be bounded between 0.0 and 1.0")

    # 9. Check business invariant: completed_plays <= plays
    invalid_completions = df[df["completed_plays"] > df["plays"]]
    if not invalid_completions.empty:
        raise DataValidationError(
            f"Business invariant violation: completed_plays exceeds plays in {len(invalid_completions)} rows"
        )
