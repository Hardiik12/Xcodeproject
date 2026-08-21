"""Deterministic statistical anomaly detection using Interquartile Range (IQR)."""

from typing import Any, Dict, List, Optional
import numpy as np
import pandas as pd

from app.processing.aggregations import aggregate_by_date


def detect_anomalies_iqr(
    df: pd.DataFrame,
    metrics: Optional[List[str]] = None,
) -> List[Dict[str, Any]]:
    """Detect statistical outliers across daily time-series metrics using the Interquartile Range (IQR) method.

    IQR Outlier Detection Method:
        Q1 (25th percentile)
        Q3 (75th percentile)
        IQR = Q3 - Q1
        lower_bound = max(0.0, Q1 - 1.5 * IQR)
        upper_bound = Q3 + 1.5 * IQR

    Severity Classification:
        Extreme Outlier (value > Q3 + 3.0 * IQR or value < Q1 - 3.0 * IQR) -> HIGH
        Moderate Outlier (value > Q3 + 1.5 * IQR or value < Q1 - 1.5 * IQR) -> MEDIUM
        Borderline Outlier                                                 -> LOW

    Disclaimer:
        These are purely deterministic statistical anomalies and outliers.
        They do NOT represent fraud, security breaches, or malicious activity.
    """
    if df.empty:
        return []

    target_metrics = metrics or ["plays", "watch_time_seconds", "playback_errors", "buffering_events"]
    daily = aggregate_by_date(df)
    if len(daily) < 4:
        # Insufficient sample size for reliable IQR quartile calculation
        return []

    anomalies: List[Dict[str, Any]] = []

    for metric in target_metrics:
        if metric not in daily.columns:
            continue

        values = daily[metric].to_numpy(dtype=float)
        q1 = float(np.percentile(values, 25))
        q3 = float(np.percentile(values, 75))
        iqr = q3 - q1

        # If constant dataset (IQR == 0), no statistical outliers exist
        if iqr <= 0.0:
            continue

        lower_bound = max(0.0, q1 - 1.5 * iqr)
        upper_bound = q3 + 1.5 * iqr
        extreme_lower = max(0.0, q1 - 3.0 * iqr)
        extreme_upper = q3 + 3.0 * iqr

        for _, row in daily.iterrows():
            val = float(row[metric])
            if val < lower_bound or val > upper_bound:
                # Classify severity
                if val < extreme_lower or val > extreme_upper:
                    severity = "HIGH"
                else:
                    severity = "MEDIUM"

                date_str = str(pd.to_datetime(row["date"]).date())
                anomalies.append(
                    {
                        "date": date_str,
                        "metric": metric,
                        "value": round(val, 2),
                        "lower_bound": round(lower_bound, 2),
                        "upper_bound": round(upper_bound, 2),
                        "severity": severity,
                    }
                )

    # Deterministic sorting: by date ASC, metric ASC
    anomalies.sort(key=lambda x: (x["date"], x["metric"]))
    return anomalies
