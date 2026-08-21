"""Analytics calculation module for Phase 7.4 advanced statistical & business analytics."""

from app.analytics.engagement import calculate_engagement_metrics
from app.analytics.performance import calculate_content_performance_scores
from app.analytics.growth import calculate_growth_rate, calculate_period_growth, calculate_daily_trends
from app.analytics.distributions import calculate_distribution_metrics, calculate_dataset_distributions
from app.analytics.anomalies import detect_anomalies_iqr
from app.analytics.insights import evaluate_content_insights

__all__ = [
    "calculate_engagement_metrics",
    "calculate_content_performance_scores",
    "calculate_growth_rate",
    "calculate_period_growth",
    "calculate_daily_trends",
    "calculate_distribution_metrics",
    "calculate_dataset_distributions",
    "detect_anomalies_iqr",
    "evaluate_content_insights",
]
