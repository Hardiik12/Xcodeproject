"""Deterministic aggregation routines for analytics DataFrames."""

from typing import List
import numpy as np
import pandas as pd

AGGREGATION_METRIC_COLUMNS = [
    "sessions",
    "plays",
    "unique_viewers",
    "watch_time_seconds",
    "completed_plays",
    "buffering_events",
    "playback_errors",
    "quality_changes",
]


def _recalculate_completion_rate(df: pd.DataFrame) -> pd.DataFrame:
    """Calculate aggregated completion_rate = completed_plays / plays (0.0 if plays == 0).

    NumPy vectorized division is utilized for exact mathematical accuracy and performance.
    """
    if df.empty:
        df["completion_rate"] = pd.Series(dtype="float64")
        return df

    plays_arr = df["plays"].to_numpy(dtype=np.float64)
    completed_arr = df["completed_plays"].to_numpy(dtype=np.float64)

    # Safe vectorized division using NumPy
    with np.errstate(divide="ignore", invalid="ignore"):
        rate = np.where(plays_arr > 0, completed_arr / plays_arr, 0.0)
        rate = np.nan_to_num(rate, nan=0.0, posinf=0.0, neginf=0.0)

    df["completion_rate"] = np.round(rate, 4)
    return df


def aggregate_by_content(df: pd.DataFrame) -> pd.DataFrame:
    """Aggregate metrics grouped by content_id."""
    if df.empty:
        cols = ["content_id"] + AGGREGATION_METRIC_COLUMNS + ["completion_rate"]
        return pd.DataFrame(columns=cols)

    grouped = (
        df.groupby("content_id", as_index=False)[AGGREGATION_METRIC_COLUMNS]
        .sum()
        .sort_values(by=["plays", "content_id"], ascending=[False, True])
    )
    return _recalculate_completion_rate(grouped)


def aggregate_by_category(df: pd.DataFrame) -> pd.DataFrame:
    """Aggregate metrics grouped by category_id (including uncategorized as None/NA)."""
    if df.empty:
        cols = ["category_id"] + AGGREGATION_METRIC_COLUMNS + ["completion_rate"]
        return pd.DataFrame(columns=cols)

    grouped = (
        df.groupby("category_id", dropna=False, as_index=False)[AGGREGATION_METRIC_COLUMNS]
        .sum()
        .sort_values(by=["plays"], ascending=[False])
    )
    return _recalculate_completion_rate(grouped)


def aggregate_by_language(df: pd.DataFrame) -> pd.DataFrame:
    """Aggregate metrics grouped by language_id (including unassigned as None/NA)."""
    if df.empty:
        cols = ["language_id"] + AGGREGATION_METRIC_COLUMNS + ["completion_rate"]
        return pd.DataFrame(columns=cols)

    grouped = (
        df.groupby("language_id", dropna=False, as_index=False)[AGGREGATION_METRIC_COLUMNS]
        .sum()
        .sort_values(by=["plays"], ascending=[False])
    )
    return _recalculate_completion_rate(grouped)


def aggregate_by_platform(df: pd.DataFrame) -> pd.DataFrame:
    """Aggregate metrics grouped by playback platform."""
    if df.empty:
        cols = ["platform"] + AGGREGATION_METRIC_COLUMNS + ["completion_rate"]
        return pd.DataFrame(columns=cols)

    grouped = (
        df.groupby("platform", as_index=False)[AGGREGATION_METRIC_COLUMNS]
        .sum()
        .sort_values(by=["plays", "platform"], ascending=[False, True])
    )
    return _recalculate_completion_rate(grouped)


def aggregate_by_date(df: pd.DataFrame) -> pd.DataFrame:
    """Aggregate metrics grouped by daily metric date in UTC."""
    if df.empty:
        cols = ["date"] + AGGREGATION_METRIC_COLUMNS + ["completion_rate"]
        return pd.DataFrame(columns=cols)

    grouped = (
        df.groupby("date", as_index=False)[AGGREGATION_METRIC_COLUMNS]
        .sum()
        .sort_values(by=["date"], ascending=[True])
    )
    return _recalculate_completion_rate(grouped)
