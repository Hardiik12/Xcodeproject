"""Period-over-period growth calculations and daily trend time-series analysis."""

from datetime import date
from typing import Any, Dict, List, Optional, Tuple
import pandas as pd

from app.processing.aggregations import aggregate_by_date


def calculate_growth_rate(current: float, previous: float) -> Tuple[float, str]:
    """Calculate percentage growth and directional trend between two periods.

    Formulas:
        If previous > 0:
            growth = ((current - previous) / previous) * 100.0
        If previous == 0 and current > 0:
            growth = 100.0
        If previous == 0 and current == 0:
            growth = 0.0
        If previous > 0 and current == 0:
            growth = -100.0

    Trends:
        growth > 0.0  -> UP
        growth < 0.0  -> DOWN
        growth == 0.0 -> FLAT
    """
    if previous == 0.0:
        if current > 0.0:
            return 100.0, "UP"
        return 0.0, "FLAT"

    growth = ((current - previous) / previous) * 100.0
    growth_rounded = round(growth, 2)

    if growth_rounded > 0.0:
        trend = "UP"
    elif growth_rounded < 0.0:
        trend = "DOWN"
    else:
        trend = "FLAT"

    return growth_rounded, trend


def calculate_period_growth(
    df: pd.DataFrame,
    split_date: Optional[date] = None,
) -> Dict[str, Any]:
    """Compute period-over-period growth by partitioning the dataset into previous and current periods.

    If split_date is not provided, the dataset's unique dates are partitioned into two equal halves.
    """
    if df.empty:
        today_str = str(date.today())
        empty_growth = {"current_value": 0.0, "previous_value": 0.0, "growth_percentage": 0.0, "trend": "FLAT"}
        return {
            "current_period_start": today_str,
            "current_period_end": today_str,
            "previous_period_start": today_str,
            "previous_period_end": today_str,
            "metrics": {
                "sessions": empty_growth,
                "plays": empty_growth,
                "unique_viewers": empty_growth,
                "watch_time_seconds": empty_growth,
                "completed_plays": empty_growth,
            },
        }

    daily = aggregate_by_date(df)
    daily["metric_date"] = pd.to_datetime(daily["date"]).dt.date
    sorted_daily = daily.sort_values("metric_date").reset_index(drop=True)
    all_dates = sorted_daily["metric_date"].tolist()

    if split_date:
        prev_df = sorted_daily[sorted_daily["metric_date"] < split_date]
        curr_df = sorted_daily[sorted_daily["metric_date"] >= split_date]
    else:
        num_dates = len(all_dates)
        if num_dates <= 1:
            prev_df = sorted_daily.iloc[0:0]  # empty
            curr_df = sorted_daily
        else:
            midpoint = num_dates // 2
            prev_df = sorted_daily.iloc[:midpoint]
            curr_df = sorted_daily.iloc[midpoint:]

    prev_start = str(prev_df["metric_date"].min()) if not prev_df.empty else (str(all_dates[0]) if all_dates else "")
    prev_end = str(prev_df["metric_date"].max()) if not prev_df.empty else (str(all_dates[0]) if all_dates else "")
    curr_start = str(curr_df["metric_date"].min()) if not curr_df.empty else (str(all_dates[-1]) if all_dates else "")
    curr_end = str(curr_df["metric_date"].max()) if not curr_df.empty else (str(all_dates[-1]) if all_dates else "")

    metrics_to_evaluate = [
        "sessions",
        "plays",
        "unique_viewers",
        "watch_time_seconds",
        "completed_plays",
    ]

    growth_results: Dict[str, Dict[str, Any]] = {}
    for metric in metrics_to_evaluate:
        prev_val = float(prev_df[metric].sum()) if not prev_df.empty else 0.0
        curr_val = float(curr_df[metric].sum()) if not curr_df.empty else 0.0
        growth_pct, trend = calculate_growth_rate(curr_val, prev_val)
        growth_results[metric] = {
            "current_value": round(curr_val, 2),
            "previous_value": round(prev_val, 2),
            "growth_percentage": growth_pct,
            "trend": trend,
        }

    return {
        "current_period_start": curr_start,
        "current_period_end": curr_end,
        "previous_period_start": prev_start,
        "previous_period_end": prev_end,
        "metrics": growth_results,
    }


def calculate_daily_trends(df: pd.DataFrame) -> List[Dict[str, Any]]:
    """Compute chronological daily summaries with day-over-day growth tracking.

    Missing Date Strategy:
        Outputs records for all calendar dates present in the contract dataset,
        sorted in ascending chronological order in UTC.
    """
    if df.empty:
        return []

    daily = aggregate_by_date(df)
    daily["date_obj"] = pd.to_datetime(daily["date"]).dt.date
    sorted_daily = daily.sort_values("date_obj").reset_index(drop=True)

    results: List[Dict[str, Any]] = []
    prev_plays: Optional[float] = None
    prev_watch_time: Optional[float] = None
    prev_viewers: Optional[float] = None

    for _, row in sorted_daily.iterrows():
        cur_plays = float(row["plays"])
        cur_watch_time = float(row["watch_time_seconds"])
        cur_viewers = float(row["unique_viewers"])

        dod_plays: Optional[float] = None
        dod_watch: Optional[float] = None
        dod_viewers: Optional[float] = None

        if prev_plays is not None:
            dod_plays, _ = calculate_growth_rate(cur_plays, prev_plays)
        if prev_watch_time is not None:
            dod_watch, _ = calculate_growth_rate(cur_watch_time, prev_watch_time)
        if prev_viewers is not None:
            dod_viewers, _ = calculate_growth_rate(cur_viewers, prev_viewers)

        date_str = str(pd.to_datetime(row["date"]).date())
        results.append(
            {
                "metric_date": date_str,
                "sessions": int(row["sessions"]),
                "plays": int(row["plays"]),
                "unique_viewers": int(row["unique_viewers"]),
                "watch_time_seconds": int(row["watch_time_seconds"]),
                "completed_plays": int(row["completed_plays"]),
                "completion_rate": float(row["completion_rate"]),
                "day_over_day_plays_growth": dod_plays,
                "day_over_day_watch_time_growth": dod_watch,
                "day_over_day_viewers_growth": dod_viewers,
            }
        )

        prev_plays = cur_plays
        prev_watch_time = cur_watch_time
        prev_viewers = cur_viewers

    return results
