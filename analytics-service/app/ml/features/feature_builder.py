"""Core ML feature engineering orchestration, lag/rolling features, and leakage protection."""

from typing import List, Optional, Set
import numpy as np
import pandas as pd

from app.ml.features.content_features import compute_content_share_features
from app.ml.features.engagement_features import compute_engagement_features
from app.ml.features.temporal_features import compute_temporal_features
from app.processing.errors import DataValidationError, SensitiveDataError

TARGET_COLUMNS: List[str] = [
    "target_next_day_plays",
    "target_next_day_watch_time",
    "target_next_day_completion_rate",
]

BASE_FEATURE_COLUMNS: List[str] = [
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
    "plays_per_session",
    "plays_per_viewer",
    "watch_time_per_play",
    "watch_time_per_session",
    "completed_plays_ratio",
    "buffering_rate",
    "error_rate",
    "quality_change_rate",
    "day_of_week",
    "day_of_month",
    "day_of_year",
    "week_of_year",
    "month",
    "quarter",
    "is_weekend",
    "content_play_share",
    "content_watch_time_share",
    "content_viewer_share",
    "plays_lag_1d",
    "watch_time_lag_1d",
    "viewers_lag_1d",
    "completion_rate_lag_1d",
    "plays_rolling_7d",
    "watch_time_rolling_7d",
    "viewers_rolling_7d",
    "completion_rate_rolling_7d",
    "plays_growth_1d",
    "watch_time_growth_1d",
    "viewer_growth_1d",
]


class MLFeatureError(DataValidationError):
    """Exception raised when feature engineering or validation violates ML safety constraints."""
    pass


def _calculate_safe_growth_ratio(current_arr: np.ndarray, prev_arr: np.ndarray) -> np.ndarray:
    """Calculate ratio growth (current - prev) / prev safely without Infinities or NaNs."""
    with np.errstate(divide="ignore", invalid="ignore"):
        ratio = np.where(
            prev_arr > 0,
            (current_arr - prev_arr) / prev_arr,
            np.where(current_arr > 0, 1.0, 0.0),
        )
        return np.nan_to_num(ratio, nan=0.0, posinf=0.0, neginf=0.0).round(4)


def compute_lag_and_rolling_features(df: pd.DataFrame) -> pd.DataFrame:
    """Compute historical lag (1-day) and rolling (7-day) metrics on (content_id, platform) groupings.

    Data Leakage Prevention:
        - DataFrame is deterministically sorted by [content_id, platform, date] in ascending order.
        - Lags use strictly shift(1), referencing only past days.
        - Rolling windows (min_periods=1) use only the current and preceding 6 days (7 days maximum).
        - No future records or target columns are referenced.
    """
    if df.empty:
        out = df.copy()
        for col in [
            "plays_lag_1d",
            "watch_time_lag_1d",
            "viewers_lag_1d",
            "completion_rate_lag_1d",
            "plays_rolling_7d",
            "watch_time_rolling_7d",
            "viewers_rolling_7d",
            "completion_rate_rolling_7d",
            "plays_growth_1d",
            "watch_time_growth_1d",
            "viewer_growth_1d",
        ]:
            out[col] = pd.Series(dtype="float64")
        return out

    # Ensure deterministic chronological sorting
    sorted_df = df.sort_values(
        by=["content_id", "platform", "date"], ascending=[True, True, True]
    ).reset_index(drop=True)

    group_keys = ["content_id", "platform"]

    # 1. Lag Features (1-Day Prior)
    sorted_df["plays_lag_1d"] = (
        sorted_df.groupby(group_keys)["plays"].shift(1).fillna(0.0).astype("float64")
    )
    sorted_df["watch_time_lag_1d"] = (
        sorted_df.groupby(group_keys)["watch_time_seconds"].shift(1).fillna(0.0).astype("float64")
    )
    sorted_df["viewers_lag_1d"] = (
        sorted_df.groupby(group_keys)["unique_viewers"].shift(1).fillna(0.0).astype("float64")
    )
    sorted_df["completion_rate_lag_1d"] = (
        sorted_df.groupby(group_keys)["completion_rate"].shift(1).fillna(0.0).astype("float64")
    )

    # 2. Rolling 7-Day Window Features (Current day + past 6 days, no future lookahead)
    rolling_grp = sorted_df.groupby(group_keys)
    sorted_df["plays_rolling_7d"] = (
        rolling_grp["plays"].rolling(7, min_periods=1).sum().reset_index(drop=True).astype("float64")
    )
    sorted_df["watch_time_rolling_7d"] = (
        rolling_grp["watch_time_seconds"].rolling(7, min_periods=1).sum().reset_index(drop=True).astype("float64")
    )
    sorted_df["viewers_rolling_7d"] = (
        rolling_grp["unique_viewers"].rolling(7, min_periods=1).sum().reset_index(drop=True).astype("float64")
    )
    rolling_completed = (
        rolling_grp["completed_plays"].rolling(7, min_periods=1).sum().reset_index(drop=True).astype("float64")
    )

    with np.errstate(divide="ignore", invalid="ignore"):
        r_plays = sorted_df["plays_rolling_7d"].to_numpy(dtype=float)
        r_comp = rolling_completed.to_numpy(dtype=float)
        sorted_df["completion_rate_rolling_7d"] = np.nan_to_num(
            np.where(r_plays > 0, r_comp / r_plays, 0.0), nan=0.0, posinf=0.0, neginf=0.0
        ).round(4)

    # 3. Growth Features (1D Ratio)
    sorted_df["plays_growth_1d"] = _calculate_safe_growth_ratio(
        sorted_df["plays"].to_numpy(dtype=float),
        sorted_df["plays_lag_1d"].to_numpy(dtype=float),
    )
    sorted_df["watch_time_growth_1d"] = _calculate_safe_growth_ratio(
        sorted_df["watch_time_seconds"].to_numpy(dtype=float),
        sorted_df["watch_time_lag_1d"].to_numpy(dtype=float),
    )
    sorted_df["viewer_growth_1d"] = _calculate_safe_growth_ratio(
        sorted_df["unique_viewers"].to_numpy(dtype=float),
        sorted_df["viewers_lag_1d"].to_numpy(dtype=float),
    )

    return sorted_df


def compute_target_variables(df: pd.DataFrame) -> pd.DataFrame:
    """Generate future-compatible target variables using shift(-1) lookahead.

    CRITICAL SAFETY RULE:
        Target variables are generated solely for supervised learning labels.
        They must NEVER be passed as input features into ML training or preprocessing pipelines.
    """
    if df.empty:
        out = df.copy()
        for col in TARGET_COLUMNS:
            out[col] = pd.Series(dtype="float64")
        return out

    sorted_df = df.sort_values(
        by=["content_id", "platform", "date"], ascending=[True, True, True]
    ).reset_index(drop=True)

    group_keys = ["content_id", "platform"]
    sorted_df["target_next_day_plays"] = (
        sorted_df.groupby(group_keys)["plays"].shift(-1).astype("float64")
    )
    sorted_df["target_next_day_watch_time"] = (
        sorted_df.groupby(group_keys)["watch_time_seconds"].shift(-1).astype("float64")
    )
    sorted_df["target_next_day_completion_rate"] = (
        sorted_df.groupby(group_keys)["completion_rate"].shift(-1).astype("float64")
    )

    return sorted_df


def build_ml_features(
    df: pd.DataFrame,
    include_targets: bool = False,
) -> pd.DataFrame:
    """Construct complete ML feature dataset from validated analytics DataFrame.

    Order of Operations:
        1. Engagement features
        2. Temporal features (UTC)
        3. Content relative share features
        4. Historical lag and rolling 7-day features
        5. (Optional) Supervised target variable generation
        6. Deterministic column ordering
    """
    if df.empty:
        empty_cols = ["date"] + BASE_FEATURE_COLUMNS + (TARGET_COLUMNS if include_targets else [])
        return pd.DataFrame(columns=empty_cols)

    # 1. Base engagement
    step1 = compute_engagement_features(df)
    # 2. Temporal UTC
    step2 = compute_temporal_features(step1)
    # 3. Content relative shares
    step3 = compute_content_share_features(step2)
    # 4. Lag and rolling historical features
    step4 = compute_lag_and_rolling_features(step3)

    if include_targets:
        step5 = compute_target_variables(step4)
        desired_cols = ["date"] + BASE_FEATURE_COLUMNS + TARGET_COLUMNS
    else:
        step5 = step4
        desired_cols = ["date"] + BASE_FEATURE_COLUMNS

    return step5[desired_cols].reset_index(drop=True)


def validate_features(
    df: pd.DataFrame,
    is_input_features_only: bool = True,
) -> None:
    """Enforce strict ML feature integrity, numerical range boundaries, and data leakage safeguards.

    Raises:
        SensitiveDataError: If any sensitive PII fields are present.
        MLFeatureError: If target leakage is detected in input features,
                        or if NaN/Inf/invalid ranges exist.
    """
    if df.empty:
        return

    # 1. Strict PII Prevention
    forbidden_pii = {
        "user_id",
        "email",
        "phone",
        "name",
        "ip_address",
        "device_id",
        "session_id",
        "token",
        "password",
        "otp",
        "user_agent",
    }
    present_cols = set(df.columns.str.lower())
    found_pii = present_cols.intersection(forbidden_pii)
    if found_pii:
        raise SensitiveDataError(
            f"Feature dataset contains forbidden sensitive PII column(s): {sorted(found_pii)}"
        )

    # 2. Target Leakage Protection (for input feature datasets)
    if is_input_features_only:
        found_targets = set(df.columns).intersection(set(TARGET_COLUMNS))
        if found_targets:
            raise MLFeatureError(
                f"Data Leakage Violation: Target column(s) {sorted(found_targets)} "
                "detected in input feature dataset! Target features must never be used as model inputs."
            )

    # 3. Infinity Checks
    numeric_cols = df.select_dtypes(include=[np.number]).columns
    for col in numeric_cols:
        if np.isinf(df[col]).any():
            raise MLFeatureError(f"Feature column '{col}' contains illegal Infinite values.")

    # 4. Bound & Range Validation
    if "completion_rate" in df.columns:
        cr = df["completion_rate"].dropna()
        if (cr < 0.0).any() or (cr > 1.0).any():
            raise MLFeatureError("Feature 'completion_rate' contains values outside valid range [0.0, 1.0].")

    if "platform" in df.columns:
        valid_platforms = {"IOS", "ANDROID", "WEB"}
        invalid = set(df["platform"].dropna().unique()) - valid_platforms
        if invalid:
            raise MLFeatureError(f"Invalid platform categories detected in feature dataset: {invalid}")
