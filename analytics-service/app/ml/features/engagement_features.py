"""Engagement and streaming reliability feature engineering functions."""

import numpy as np
import pandas as pd


def compute_engagement_features(df: pd.DataFrame) -> pd.DataFrame:
    """Compute per-row derived viewer engagement and player reliability ratios.

    Safe Division:
        For any metric division where the denominator is zero, exactly 0.0 is assigned.
        No NaNs or Infinities are produced.
    """
    out = df.copy()
    if out.empty:
        for col in [
            "plays_per_session",
            "plays_per_viewer",
            "watch_time_per_play",
            "watch_time_per_session",
            "completed_plays_ratio",
            "buffering_rate",
            "error_rate",
            "quality_change_rate",
        ]:
            out[col] = pd.Series(dtype="float64")
        return out

    sessions = out["sessions"].to_numpy(dtype=float)
    plays = out["plays"].to_numpy(dtype=float)
    viewers = out["unique_viewers"].to_numpy(dtype=float)
    watch_time = out["watch_time_seconds"].to_numpy(dtype=float)
    completed = out["completed_plays"].to_numpy(dtype=float)
    buffering = out["buffering_events"].to_numpy(dtype=float)
    errors = out["playback_errors"].to_numpy(dtype=float)
    quality = out["quality_changes"].to_numpy(dtype=float)

    with np.errstate(divide="ignore", invalid="ignore"):
        out["plays_per_session"] = np.nan_to_num(
            np.where(sessions > 0, plays / sessions, 0.0), nan=0.0, posinf=0.0, neginf=0.0
        ).round(4)

        out["plays_per_viewer"] = np.nan_to_num(
            np.where(viewers > 0, plays / viewers, 0.0), nan=0.0, posinf=0.0, neginf=0.0
        ).round(4)

        out["watch_time_per_play"] = np.nan_to_num(
            np.where(plays > 0, watch_time / plays, 0.0), nan=0.0, posinf=0.0, neginf=0.0
        ).round(2)

        out["watch_time_per_session"] = np.nan_to_num(
            np.where(sessions > 0, watch_time / sessions, 0.0), nan=0.0, posinf=0.0, neginf=0.0
        ).round(2)

        out["completed_plays_ratio"] = np.nan_to_num(
            np.where(sessions > 0, completed / sessions, 0.0), nan=0.0, posinf=0.0, neginf=0.0
        ).round(4)

        out["buffering_rate"] = np.nan_to_num(
            np.where(plays > 0, buffering / plays, 0.0), nan=0.0, posinf=0.0, neginf=0.0
        ).round(4)

        out["error_rate"] = np.nan_to_num(
            np.where(plays > 0, errors / plays, 0.0), nan=0.0, posinf=0.0, neginf=0.0
        ).round(4)

        out["quality_change_rate"] = np.nan_to_num(
            np.where(plays > 0, quality / plays, 0.0), nan=0.0, posinf=0.0, neginf=0.0
        ).round(4)

    return out
