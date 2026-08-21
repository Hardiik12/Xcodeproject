"""Content performance scoring and rankings based on deterministic business heuristics."""

from typing import List
import pandas as pd

from app.processing.aggregations import aggregate_by_content


def calculate_content_performance_scores(
    df: pd.DataFrame,
    by: str = "performance_score",
    limit: int = 10,
) -> pd.DataFrame:
    """Calculate normalized engagement metrics, composite performance scores, and rankings.

    Normalization Formula:
        normalized(x) = (x - min(x)) / (max(x) - min(x)) if max(x) > min(x) else 0.0

    Performance Score Formula (Range 0.0 to 100.0):
        performance_score = (
            0.35 * norm_plays +
            0.25 * norm_watch_time +
            0.20 * norm_viewers +
            0.20 * completion_rate
        ) * 100.0

    Note:
        This is a deterministic business analytics scoring model and NOT a machine learning algorithm.
        Deterministic tie-breaking is enforced by sorting descending on the target metric,
        then ascending on content_id.
    """
    if df.empty:
        return pd.DataFrame()

    agg = aggregate_by_content(df)
    if agg.empty:
        return pd.DataFrame()

    # Normalization helper
    def min_max_normalize(series: pd.Series) -> pd.Series:
        min_v = series.min()
        max_v = series.max()
        if max_v > min_v:
            return (series - min_v) / (max_v - min_v)
        return pd.Series(0.0, index=series.index, dtype=float)

    agg["normalized_plays"] = min_max_normalize(agg["plays"]).round(4)
    agg["normalized_watch_time"] = min_max_normalize(agg["watch_time_seconds"]).round(4)
    agg["normalized_viewers"] = min_max_normalize(agg["unique_viewers"]).round(4)

    # Compute composite performance score (0.0 - 100.0)
    agg["performance_score"] = (
        (
            0.35 * agg["normalized_plays"]
            + 0.25 * agg["normalized_watch_time"]
            + 0.20 * agg["normalized_viewers"]
            + 0.20 * agg["completion_rate"]
        )
        * 100.0
    ).round(2)

    # Map sort column aliases
    sort_column_map = {
        "plays": "plays",
        "watch_time": "watch_time_seconds",
        "watch_time_seconds": "watch_time_seconds",
        "unique_viewers": "unique_viewers",
        "viewers": "unique_viewers",
        "completion_rate": "completion_rate",
        "performance_score": "performance_score",
        "score": "performance_score",
    }
    sort_col = sort_column_map.get(by, "performance_score")

    # Sort deterministically
    sorted_df = agg.sort_values(
        by=[sort_col, "content_id"],
        ascending=[False, True],
    ).reset_index(drop=True)

    # Apply limit (bounded between 1 and 100)
    bounded_limit = max(1, min(limit, 100))
    result_df = sorted_df.head(bounded_limit).copy()
    result_df["rank"] = range(1, len(result_df) + 1)

    return result_df
