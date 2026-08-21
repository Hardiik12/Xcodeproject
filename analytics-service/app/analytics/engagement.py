"""Engagement analytics calculations for viewing behavior, completion rates, and player reliability."""

from typing import Any, Dict
import pandas as pd


def calculate_engagement_metrics(df: pd.DataFrame) -> Dict[str, Any]:
    """Calculate deterministic viewing and engagement metrics from validated analytics records.

    Formulas:
        overall_completion_rate        = total_completed_plays / total_plays (or 0.0)
        average_watch_time_per_play    = total_watch_time_seconds / total_plays (or 0.0)
        average_watch_time_per_session = total_watch_time_seconds / total_sessions (or 0.0)
        plays_per_viewer               = total_plays / total_unique_viewers (or 0.0)
        sessions_per_viewer            = total_sessions / total_unique_viewers (or 0.0)
        completion_ratio               = total_completed_plays / total_plays (or 0.0)
        buffering_rate                 = total_buffering_events / total_plays (or 0.0)
        playback_error_rate            = total_playback_errors / total_plays (or 0.0)
        quality_change_rate            = total_quality_changes / total_plays (or 0.0)
    """
    if df.empty:
        return {
            "total_sessions": 0,
            "total_plays": 0,
            "total_unique_viewers": 0,
            "total_watch_time_seconds": 0,
            "total_completed_plays": 0,
            "overall_completion_rate": 0.0,
            "average_watch_time_per_play": 0.0,
            "average_watch_time_per_session": 0.0,
            "plays_per_viewer": 0.0,
            "sessions_per_viewer": 0.0,
            "completion_ratio": 0.0,
            "buffering_rate": 0.0,
            "playback_error_rate": 0.0,
            "quality_change_rate": 0.0,
        }

    total_sessions = int(df["sessions"].sum())
    total_plays = int(df["plays"].sum())
    total_unique_viewers = int(df["unique_viewers"].sum())
    total_watch_time_seconds = int(df["watch_time_seconds"].sum())
    total_completed_plays = int(df["completed_plays"].sum())
    total_buffering = int(df["buffering_events"].sum())
    total_errors = int(df["playback_errors"].sum())
    total_quality_changes = int(df["quality_changes"].sum())

    # Safe division guards
    overall_completion_rate = (
        float(total_completed_plays) / float(total_plays) if total_plays > 0 else 0.0
    )
    avg_watch_time_play = (
        float(total_watch_time_seconds) / float(total_plays) if total_plays > 0 else 0.0
    )
    avg_watch_time_session = (
        float(total_watch_time_seconds) / float(total_sessions) if total_sessions > 0 else 0.0
    )
    plays_per_viewer = (
        float(total_plays) / float(total_unique_viewers) if total_unique_viewers > 0 else 0.0
    )
    sessions_per_viewer = (
        float(total_sessions) / float(total_unique_viewers) if total_unique_viewers > 0 else 0.0
    )
    buffering_rate = (
        float(total_buffering) / float(total_plays) if total_plays > 0 else 0.0
    )
    playback_error_rate = (
        float(total_errors) / float(total_plays) if total_plays > 0 else 0.0
    )
    quality_change_rate = (
        float(total_quality_changes) / float(total_plays) if total_plays > 0 else 0.0
    )

    return {
        "total_sessions": total_sessions,
        "total_plays": total_plays,
        "total_unique_viewers": total_unique_viewers,
        "total_watch_time_seconds": total_watch_time_seconds,
        "total_completed_plays": total_completed_plays,
        "overall_completion_rate": round(overall_completion_rate, 4),
        "average_watch_time_per_play": round(avg_watch_time_play, 2),
        "average_watch_time_per_session": round(avg_watch_time_session, 2),
        "plays_per_viewer": round(plays_per_viewer, 2),
        "sessions_per_viewer": round(sessions_per_viewer, 2),
        "completion_ratio": round(overall_completion_rate, 4),
        "buffering_rate": round(buffering_rate, 4),
        "playback_error_rate": round(playback_error_rate, 4),
        "quality_change_rate": round(quality_change_rate, 4),
    }
