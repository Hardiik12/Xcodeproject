"""Distribution analysis and percentile statistics using Pandas and NumPy."""

from typing import Any, Dict
import numpy as np
import pandas as pd


def calculate_distribution_metrics(series: pd.Series) -> Dict[str, Any]:
    """Calculate sample distribution metrics and standard percentiles for a continuous series.

    Formulas & Conventions:
        Count              = N observations
        Mean               = (1/N) * sum(x_i)
        Median             = P50 (50th percentile)
        Sample Std Dev     = sqrt( (1 / (N - 1)) * sum((x_i - mean)^2) ) with ddof=1
                             (returns 0.0 if N <= 1)
        Percentiles        = P25 (Q1), P50 (Median), P75 (Q3), P90, P95
    """
    clean_series = series.dropna()
    n = len(clean_series)

    if n == 0:
        return {
            "count": 0,
            "mean": 0.0,
            "median": 0.0,
            "minimum": 0.0,
            "maximum": 0.0,
            "standard_deviation": 0.0,
            "p25": 0.0,
            "p50": 0.0,
            "p75": 0.0,
            "p90": 0.0,
            "p95": 0.0,
        }

    values = clean_series.to_numpy(dtype=float)
    mean_val = float(np.mean(values))
    median_val = float(np.median(values))
    min_val = float(np.min(values))
    max_val = float(np.max(values))
    std_val = float(np.std(values, ddof=1)) if n > 1 else 0.0

    p25 = float(np.percentile(values, 25))
    p50 = float(np.percentile(values, 50))
    p75 = float(np.percentile(values, 75))
    p90 = float(np.percentile(values, 90))
    p95 = float(np.percentile(values, 95))

    return {
        "count": n,
        "mean": round(mean_val, 4),
        "median": round(median_val, 4),
        "minimum": round(min_val, 4),
        "maximum": round(max_val, 4),
        "standard_deviation": round(std_val, 4),
        "p25": round(p25, 4),
        "p50": round(p50, 4),
        "p75": round(p75, 4),
        "p90": round(p90, 4),
        "p95": round(p95, 4),
    }


def calculate_dataset_distributions(df: pd.DataFrame) -> Dict[str, Dict[str, Any]]:
    """Compute distribution statistics for core continuous metric dimensions."""
    metrics = ["plays", "watch_time_seconds", "completion_rate", "unique_viewers"]
    results: Dict[str, Dict[str, Any]] = {}

    for metric in metrics:
        if df.empty or metric not in df.columns:
            results[metric] = calculate_distribution_metrics(pd.Series(dtype=float))
        else:
            results[metric] = calculate_distribution_metrics(df[metric])

    return results
