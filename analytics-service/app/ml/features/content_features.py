"""Content relative performance and market share feature engineering."""

import numpy as np
import pandas as pd


def compute_content_share_features(df: pd.DataFrame) -> pd.DataFrame:
    """Compute daily relative market shares for each content item against the day's total volume.

    Features:
        - content_play_share: content daily plays / total daily plays across all catalog
        - content_watch_time_share: content daily watch time / total daily watch time
        - content_viewer_share: content daily viewers / total daily viewers
    """
    out = df.copy()
    if out.empty:
        for col in ["content_play_share", "content_watch_time_share", "content_viewer_share"]:
            out[col] = pd.Series(dtype="float64")
        return out

    # Compute daily totals
    daily_totals = (
        out.groupby("date", as_index=False)
        .agg(
            total_daily_plays=("plays", "sum"),
            total_daily_watch_time=("watch_time_seconds", "sum"),
            total_daily_viewers=("unique_viewers", "sum"),
        )
    )

    merged = out.merge(daily_totals, on="date", how="left")

    with np.errstate(divide="ignore", invalid="ignore"):
        d_plays = merged["total_daily_plays"].to_numpy(dtype=float)
        d_watch = merged["total_daily_watch_time"].to_numpy(dtype=float)
        d_viewers = merged["total_daily_viewers"].to_numpy(dtype=float)

        c_plays = merged["plays"].to_numpy(dtype=float)
        c_watch = merged["watch_time_seconds"].to_numpy(dtype=float)
        c_viewers = merged["unique_viewers"].to_numpy(dtype=float)

        merged["content_play_share"] = np.nan_to_num(
            np.where(d_plays > 0, c_plays / d_plays, 0.0), nan=0.0, posinf=0.0, neginf=0.0
        ).round(4)

        merged["content_watch_time_share"] = np.nan_to_num(
            np.where(d_watch > 0, c_watch / d_watch, 0.0), nan=0.0, posinf=0.0, neginf=0.0
        ).round(4)

        merged["content_viewer_share"] = np.nan_to_num(
            np.where(d_viewers > 0, c_viewers / d_viewers, 0.0), nan=0.0, posinf=0.0, neginf=0.0
        ).round(4)

    # Drop temporary daily totals columns
    return merged.drop(
        columns=["total_daily_plays", "total_daily_watch_time", "total_daily_viewers"],
        errors="ignore",
    )
