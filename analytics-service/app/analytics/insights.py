"""Deterministic business health heuristics and actionable catalog insights."""

from typing import Any, Dict, List, Optional
import pandas as pd

from app.analytics.growth import calculate_period_growth
from app.core.config import Settings, get_settings
from app.processing.aggregations import aggregate_by_content


def evaluate_content_insights(
    df: pd.DataFrame,
    settings: Optional[Settings] = None,
) -> List[Dict[str, Any]]:
    """Evaluate deterministic heuristic rules across content and platform aggregations.

    Heuristic Rules:
        1. HIGH_ENGAGEMENT: completion_rate >= 0.70 and plays >= 10
        2. LOW_COMPLETION: completion_rate < 0.30 and plays >= 5
        3. HIGH_BUFFERING: buffering_rate > 0.05 and plays >= 5
        4. HIGH_ERROR_RATE: playback_error_rate > 0.02 and plays >= 5
        5. RAPID_GROWTH: period plays growth > 25.0%
        6. DECLINING_CONTENT: period plays growth < -20.0%

    Note:
        These are rule-based analytics heuristics and NOT machine learning predictions.
    """
    if df.empty:
        return []

    cfg = settings or get_settings()
    insights: List[Dict[str, Any]] = []

    # Content-level evaluation
    content_agg = aggregate_by_content(df)
    for _, row in content_agg.iterrows():
        cid = int(row["content_id"])
        plays = int(row["plays"])
        comp_rate = float(row["completion_rate"])
        buf_events = int(row["buffering_events"])
        errors = int(row["playback_errors"])

        buf_rate = float(buf_events) / float(plays) if plays > 0 else 0.0
        err_rate = float(errors) / float(plays) if plays > 0 else 0.0

        # Rule 1: High Engagement
        if comp_rate >= cfg.HIGH_ENGAGEMENT_COMPLETION_THRESHOLD and plays >= 10:
            insights.append(
                {
                    "type": "HIGH_ENGAGEMENT",
                    "severity": "LOW",
                    "content_id": cid,
                    "metric": "completion_rate",
                    "value": round(comp_rate, 4),
                    "threshold": cfg.HIGH_ENGAGEMENT_COMPLETION_THRESHOLD,
                    "message": f"Content ID {cid} demonstrates strong viewer engagement with a completion rate of {comp_rate:.1%}.",
                }
            )

        # Rule 2: Low Completion
        if comp_rate < cfg.LOW_COMPLETION_RATE_THRESHOLD and plays >= 5:
            insights.append(
                {
                    "type": "LOW_COMPLETION",
                    "severity": "MEDIUM",
                    "content_id": cid,
                    "metric": "completion_rate",
                    "value": round(comp_rate, 4),
                    "threshold": cfg.LOW_COMPLETION_RATE_THRESHOLD,
                    "message": f"Content ID {cid} completion rate ({comp_rate:.1%}) is below healthy threshold ({cfg.LOW_COMPLETION_RATE_THRESHOLD:.1%}).",
                }
            )

        # Rule 3: High Buffering
        if buf_rate > cfg.HIGH_BUFFERING_RATE_THRESHOLD and plays >= 5:
            insights.append(
                {
                    "type": "HIGH_BUFFERING",
                    "severity": "HIGH",
                    "content_id": cid,
                    "metric": "buffering_rate",
                    "value": round(buf_rate, 4),
                    "threshold": cfg.HIGH_BUFFERING_RATE_THRESHOLD,
                    "message": f"Content ID {cid} experiences elevated buffering ({buf_rate:.1%} per play). Check CDN/renditions.",
                }
            )

        # Rule 4: High Error Rate
        if err_rate > cfg.HIGH_ERROR_RATE_THRESHOLD and plays >= 5:
            insights.append(
                {
                    "type": "HIGH_ERROR_RATE",
                    "severity": "HIGH",
                    "content_id": cid,
                    "metric": "playback_error_rate",
                    "value": round(err_rate, 4),
                    "threshold": cfg.HIGH_ERROR_RATE_THRESHOLD,
                    "message": f"Content ID {cid} has a playback error rate of {err_rate:.1%}, exceeding tolerance threshold.",
                }
            )

    # Platform/Growth evaluation
    growth_data = calculate_period_growth(df)
    plays_growth = growth_data["metrics"]["plays"]["growth_percentage"]

    if plays_growth >= cfg.GROWTH_ALERT_THRESHOLD:
        insights.append(
            {
                "type": "RAPID_GROWTH",
                "severity": "MEDIUM",
                "content_id": None,
                "metric": "plays_growth",
                "value": plays_growth,
                "threshold": cfg.GROWTH_ALERT_THRESHOLD,
                "message": f"Platform playback volume grew by {plays_growth:.1f}% across comparative periods.",
            }
        )
    elif plays_growth <= cfg.DECLINING_CONTENT_THRESHOLD:
        insights.append(
            {
                "type": "DECLINING_CONTENT",
                "severity": "MEDIUM",
                "content_id": None,
                "metric": "plays_growth",
                "value": plays_growth,
                "threshold": cfg.DECLINING_CONTENT_THRESHOLD,
                "message": f"Platform playback volume decreased by {abs(plays_growth):.1f}% across comparative periods.",
            }
        )

    # Deterministic sort: by severity DESC (HIGH, MEDIUM, LOW), content_id ASC
    severity_order = {"HIGH": 0, "MEDIUM": 1, "LOW": 2}
    insights.sort(key=lambda x: (severity_order.get(x["severity"], 3), x.get("content_id") or 0))

    return insights
