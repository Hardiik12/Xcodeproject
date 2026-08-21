"""Deterministic statistical computation and content ranking routines using NumPy and Pandas."""

from typing import Any, Dict, List, Optional
import numpy as np
import pandas as pd

from app.processing.aggregations import aggregate_by_content

STATS_METRICS = [
    "sessions",
    "plays",
    "unique_viewers",
    "watch_time_seconds",
    "completed_plays",
    "completion_rate",
]


def calculate_metric_statistics(df: pd.DataFrame) -> Dict[str, Dict[str, float]]:
    """Compute summary statistics for key numerical analytics metrics.

    Convention:
    - Standard Deviation: Uses **Sample Standard Deviation** (NumPy `ddof=1`).
      When sample size N <= 1, standard deviation is defined as 0.0.
    - All float values are rounded to 4 decimal places for deterministic reporting.

    Returns:
        Dictionary mapping metric names to statistical values:
        count, mean, median, minimum, maximum, standard_deviation.
    """
    results: Dict[str, Dict[str, float]] = {}

    if df.empty:
        for metric in STATS_METRICS:
            results[metric] = {
                "count": 0,
                "mean": 0.0,
                "median": 0.0,
                "minimum": 0.0,
                "maximum": 0.0,
                "standard_deviation": 0.0,
            }
        return results

    for metric in STATS_METRICS:
        if metric not in df.columns:
            continue

        series = df[metric].dropna()
        count = int(len(series))

        if count == 0:
            results[metric] = {
                "count": 0,
                "mean": 0.0,
                "median": 0.0,
                "minimum": 0.0,
                "maximum": 0.0,
                "standard_deviation": 0.0,
            }
            continue

        arr = series.to_numpy(dtype=np.float64)

        mean_val = float(np.mean(arr))
        median_val = float(np.median(arr))
        min_val = float(np.min(arr))
        max_val = float(np.max(arr))

        # Sample standard deviation (ddof=1) if count > 1
        std_val = float(np.std(arr, ddof=1)) if count > 1 else 0.0
        if np.isnan(std_val):
            std_val = 0.0

        results[metric] = {
            "count": count,
            "mean": round(mean_val, 4),
            "median": round(median_val, 4),
            "minimum": round(min_val, 4),
            "maximum": round(max_val, 4),
            "standard_deviation": round(std_val, 4),
        }

    return results


def get_top_content(
    df: pd.DataFrame,
    by: str = "plays",
    limit: int = 10,
    min_plays: int = 1,
) -> pd.DataFrame:
    """Rank top performing content assets aggregated across dates and platforms.

    Args:
        df: Input analytics DataFrame.
        by: Metric to rank by ('plays', 'watch_time_seconds', 'unique_viewers', 'completion_rate').
        limit: Max number of ranked assets to return (>= 1).
        min_plays: Minimum plays required to qualify for top ranking (default 1 to exclude unplayed items).

    Tie-breaking:
        Primary: `by` descending
        Secondary: `content_id` ascending
    """
    if df.empty:
        return aggregate_by_content(df)

    aggregated = aggregate_by_content(df)

    # Filter out assets below play threshold
    if min_plays > 0:
        aggregated = aggregated[aggregated["plays"] >= min_plays]

    if aggregated.empty:
        return aggregated

    valid_by = by if by in aggregated.columns else "plays"

    # Deterministic sorting: target metric DESC, content_id ASC
    sorted_df = aggregated.sort_values(
        by=[valid_by, "content_id"],
        ascending=[False, True],
    )

    return sorted_df.head(max(limit, 1))
