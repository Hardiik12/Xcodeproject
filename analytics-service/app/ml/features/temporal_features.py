"""Temporal calendar feature engineering based on explicit UTC date semantics."""

import pandas as pd


def compute_temporal_features(df: pd.DataFrame) -> pd.DataFrame:
    """Extract deterministic calendar and seasonality features using strict UTC date semantics.

    Features:
        - day_of_week: 0 (Monday) to 6 (Sunday)
        - day_of_month: 1 to 31
        - day_of_year: 1 to 366
        - week_of_year: 1 to 53 (ISO calendar week)
        - month: 1 to 12
        - quarter: 1 to 4
        - is_weekend: 1 for Saturday/Sunday (day_of_week >= 5), else 0
    """
    out = df.copy()
    if out.empty or "date" not in out.columns:
        for col in [
            "day_of_week",
            "day_of_month",
            "day_of_year",
            "week_of_year",
            "month",
            "quarter",
            "is_weekend",
        ]:
            out[col] = pd.Series(dtype="int64")
        return out

    # Ensure UTC datetime Series without local timezone inference
    dt_series = pd.to_datetime(out["date"], utc=True)

    out["day_of_week"] = dt_series.dt.dayofweek.astype("int64")
    out["day_of_month"] = dt_series.dt.day.astype("int64")
    out["day_of_year"] = dt_series.dt.dayofyear.astype("int64")
    out["week_of_year"] = dt_series.dt.isocalendar().week.astype("int64")
    out["month"] = dt_series.dt.month.astype("int64")
    out["quarter"] = dt_series.dt.quarter.astype("int64")
    out["is_weekend"] = (dt_series.dt.dayofweek >= 5).astype("int64")

    return out
