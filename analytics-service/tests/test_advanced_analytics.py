"""Unit tests for Phase 7.4 Advanced Statistical and Business Analytics Calculations."""

from datetime import date
import numpy as np
import pandas as pd
import pytest

from app.analytics.anomalies import detect_anomalies_iqr
from app.analytics.distributions import (
    calculate_dataset_distributions,
    calculate_distribution_metrics,
)
from app.analytics.engagement import calculate_engagement_metrics
from app.analytics.growth import (
    calculate_daily_trends,
    calculate_growth_rate,
    calculate_period_growth,
)
from app.analytics.insights import evaluate_content_insights
from app.analytics.performance import calculate_content_performance_scores
from app.processing.dataframe_builder import build_dataframe
from app.processing.data_validator import validate_dataframe
from app.processing.errors import SensitiveDataError
from app.schemas.contract import AnalyticsExportRecord, PlatformEnum


@pytest.fixture
def sample_records():
    """Deterministic fixture containing 6 multiday, multiplatform analytics records."""
    return [
        AnalyticsExportRecord(
            content_id=101,
            date=date(2026, 8, 1),
            platform=PlatformEnum.IOS,
            category_id=1,
            language_id=10,
            sessions=100,
            plays=80,
            unique_viewers=50,
            watch_time_seconds=24000,
            completed_plays=40,
            completion_rate=0.50,
            buffering_events=4,
            playback_errors=1,
            quality_changes=5,
        ),
        AnalyticsExportRecord(
            content_id=101,
            date=date(2026, 8, 2),
            platform=PlatformEnum.ANDROID,
            category_id=1,
            language_id=10,
            sessions=150,
            plays=120,
            unique_viewers=80,
            watch_time_seconds=48000,
            completed_plays=96,
            completion_rate=0.80,
            buffering_events=6,
            playback_errors=2,
            quality_changes=10,
        ),
        AnalyticsExportRecord(
            content_id=102,
            date=date(2026, 8, 1),
            platform=PlatformEnum.WEB,
            category_id=2,
            language_id=20,
            sessions=60,
            plays=50,
            unique_viewers=30,
            watch_time_seconds=10000,
            completed_plays=10,
            completion_rate=0.20,
            buffering_events=10,
            playback_errors=5,
            quality_changes=3,
        ),
        AnalyticsExportRecord(
            content_id=102,
            date=date(2026, 8, 2),
            platform=PlatformEnum.IOS,
            category_id=2,
            language_id=20,
            sessions=80,
            plays=70,
            unique_viewers=40,
            watch_time_seconds=21000,
            completed_plays=35,
            completion_rate=0.50,
            buffering_events=2,
            playback_errors=0,
            quality_changes=4,
        ),
        AnalyticsExportRecord(
            content_id=103,
            date=date(2026, 8, 3),
            platform=PlatformEnum.ANDROID,
            category_id=None,
            language_id=None,
            sessions=200,
            plays=180,
            unique_viewers=120,
            watch_time_seconds=90000,
            completed_plays=144,
            completion_rate=0.80,
            buffering_events=5,
            playback_errors=1,
            quality_changes=8,
        ),
        AnalyticsExportRecord(
            content_id=104,
            date=date(2026, 8, 4),
            platform=PlatformEnum.WEB,
            category_id=1,
            language_id=10,
            sessions=40,
            plays=30,
            unique_viewers=25,
            watch_time_seconds=6000,
            completed_plays=6,
            completion_rate=0.20,
            buffering_events=8,
            playback_errors=4,
            quality_changes=2,
        ),
    ]


@pytest.fixture
def sample_df(sample_records):
    """Clean DataFrame built from sample records."""
    df = build_dataframe(sample_records)
    validate_dataframe(df)
    return df


# 1. ENGAGEMENT ANALYTICS TESTS
def test_engagement_metrics_calculation(sample_df):
    """Test engagement calculations against exact hand-calculated totals."""
    metrics = calculate_engagement_metrics(sample_df)

    expected_sessions = 100 + 150 + 60 + 80 + 200 + 40  # 630
    expected_plays = 80 + 120 + 50 + 70 + 180 + 30  # 530
    expected_viewers = 50 + 80 + 30 + 40 + 120 + 25  # 345
    expected_watch = 24000 + 48000 + 10000 + 21000 + 90000 + 6000  # 199000
    expected_completed = 40 + 96 + 10 + 35 + 144 + 6  # 331

    assert metrics["total_sessions"] == expected_sessions
    assert metrics["total_plays"] == expected_plays
    assert metrics["total_unique_viewers"] == expected_viewers
    assert metrics["total_watch_time_seconds"] == expected_watch
    assert metrics["total_completed_plays"] == expected_completed

    assert metrics["overall_completion_rate"] == round(331 / 530, 4)
    assert metrics["average_watch_time_per_play"] == round(199000 / 530, 2)
    assert metrics["average_watch_time_per_session"] == round(199000 / 630, 2)
    assert metrics["plays_per_viewer"] == round(530 / 345, 2)
    assert metrics["sessions_per_viewer"] == round(630 / 345, 2)


def test_engagement_metrics_empty_dataframe():
    """Empty dataframe must return 0 and 0.0 with zero NaN or division-by-zero errors."""
    empty_df = pd.DataFrame()
    metrics = calculate_engagement_metrics(empty_df)
    assert metrics["total_sessions"] == 0
    assert metrics["overall_completion_rate"] == 0.0
    assert metrics["average_watch_time_per_play"] == 0.0
    assert metrics["buffering_rate"] == 0.0


def test_engagement_metrics_zero_plays():
    """Zero plays should safely return 0.0 for all derived rates."""
    df = pd.DataFrame(
        {
            "sessions": [10],
            "plays": [0],
            "unique_viewers": [5],
            "watch_time_seconds": [0],
            "completed_plays": [0],
            "buffering_events": [0],
            "playback_errors": [0],
            "quality_changes": [0],
        }
    )
    metrics = calculate_engagement_metrics(df)
    assert metrics["overall_completion_rate"] == 0.0
    assert metrics["average_watch_time_per_play"] == 0.0
    assert metrics["buffering_rate"] == 0.0
    assert metrics["sessions_per_viewer"] == 2.0


# 2. CONTENT PERFORMANCE SCORE & RANKINGS TESTS
def test_content_performance_scoring(sample_df):
    """Test min-max normalization and performance score formula."""
    res = calculate_content_performance_scores(sample_df, by="performance_score", limit=10)
    assert not res.empty
    assert len(res) == 4  # 4 distinct content IDs (101, 102, 103, 104)

    # Content 103 has the highest watch time (90000) and highest completion rate (0.80)
    top_row = res.iloc[0]
    assert top_row["content_id"] == 103
    assert top_row["rank"] == 1
    assert top_row["performance_score"] > 80.0

    # Ensure normalized values are bounded [0.0, 1.0]
    for _, row in res.iterrows():
        assert 0.0 <= row["normalized_plays"] <= 1.0
        assert 0.0 <= row["normalized_watch_time"] <= 1.0
        assert 0.0 <= row["normalized_viewers"] <= 1.0
        assert 0.0 <= row["performance_score"] <= 100.0


def test_content_performance_tie_breaking():
    """Test deterministic tie-breaking: sorts by metric DESC, then content_id ASC."""
    df = pd.DataFrame(
        [
            {
                "content_id": 200,
                "metric_date": "2026-08-01",
                "platform": "IOS",
                "category_id": 1,
                "language_id": 1,
                "sessions": 50,
                "plays": 50,
                "unique_viewers": 25,
                "watch_time_seconds": 1000,
                "completed_plays": 25,
                "completion_rate": 0.50,
                "buffering_events": 0,
                "playback_errors": 0,
                "quality_changes": 0,
            },
            {
                "content_id": 100,
                "metric_date": "2026-08-01",
                "platform": "IOS",
                "category_id": 1,
                "language_id": 1,
                "sessions": 50,
                "plays": 50,
                "unique_viewers": 25,
                "watch_time_seconds": 1000,
                "completed_plays": 25,
                "completion_rate": 0.50,
                "buffering_events": 0,
                "playback_errors": 0,
                "quality_changes": 0,
            },
        ]
    )
    res = calculate_content_performance_scores(df, by="plays", limit=10)
    assert len(res) == 2
    # content_id 100 must come before 200 due to ascending tie breaker
    assert res.iloc[0]["content_id"] == 100
    assert res.iloc[1]["content_id"] == 200


def test_content_performance_single_record_normalization():
    """When min == max (e.g. single content), normalized values must evaluate to 0.0."""
    df = pd.DataFrame(
        [
            {
                "content_id": 50,
                "metric_date": "2026-08-01",
                "platform": "IOS",
                "category_id": 1,
                "language_id": 1,
                "sessions": 50,
                "plays": 50,
                "unique_viewers": 25,
                "watch_time_seconds": 1000,
                "completed_plays": 25,
                "completion_rate": 0.50,
                "buffering_events": 0,
                "playback_errors": 0,
                "quality_changes": 0,
            }
        ]
    )
    res = calculate_content_performance_scores(df, by="performance_score", limit=10)
    assert len(res) == 1
    assert res.iloc[0]["normalized_plays"] == 0.0
    assert res.iloc[0]["normalized_watch_time"] == 0.0
    assert res.iloc[0]["normalized_viewers"] == 0.0
    # Score is 0.20 * 0.50 * 100.0 = 10.0
    assert res.iloc[0]["performance_score"] == 10.0


# 3. GROWTH & TRENDS TESTS
def test_growth_rate_formula():
    """Test period growth percentage and directional trend classification."""
    # Standard positive growth
    pct, trend = calculate_growth_rate(150.0, 100.0)
    assert pct == 50.0
    assert trend == "UP"

    # Standard negative growth
    pct, trend = calculate_growth_rate(50.0, 100.0)
    assert pct == -50.0
    assert trend == "DOWN"

    # Previous was 0, current > 0
    pct, trend = calculate_growth_rate(10.0, 0.0)
    assert pct == 100.0
    assert trend == "UP"

    # Previous was 0, current == 0
    pct, trend = calculate_growth_rate(0.0, 0.0)
    assert pct == 0.0
    assert trend == "FLAT"


def test_period_growth_calculation(sample_df):
    """Test period growth across dataset partitions."""
    growth = calculate_period_growth(sample_df)
    assert "metrics" in growth
    assert "plays" in growth["metrics"]
    assert "watch_time_seconds" in growth["metrics"]

    plays_metric = growth["metrics"]["plays"]
    assert plays_metric["current_value"] > 0
    assert plays_metric["previous_value"] > 0
    assert plays_metric["trend"] in ["UP", "DOWN", "FLAT"]


def test_daily_trends_calculation(sample_df):
    """Test chronological daily time-series with day-over-day tracking."""
    trends = calculate_daily_trends(sample_df)
    assert len(trends) == 4  # 4 distinct calendar dates (2026-08-01 to 2026-08-04)

    # First day has None for DoD growth
    assert trends[0]["metric_date"] == "2026-08-01"
    assert trends[0]["day_over_day_plays_growth"] is None

    # Subsequent days have numeric DoD growth
    assert trends[1]["metric_date"] == "2026-08-02"
    assert isinstance(trends[1]["day_over_day_plays_growth"], float)


# 4. DISTRIBUTIONS & PERCENTILES TESTS
def test_calculate_distribution_metrics_sample_std():
    """Test sample standard deviation (ddof=1) and percentiles."""
    values = pd.Series([10.0, 20.0, 30.0, 40.0, 50.0])
    stats = calculate_distribution_metrics(values)

    assert stats["count"] == 5
    assert stats["mean"] == 30.0
    assert stats["median"] == 30.0
    assert stats["minimum"] == 10.0
    assert stats["maximum"] == 50.0
    # Sample std of [10, 20, 30, 40, 50] is sqrt(1000/4) = sqrt(250) = 15.8114
    assert stats["standard_deviation"] == round(np.std([10, 20, 30, 40, 50], ddof=1), 4)
    assert stats["p25"] == 20.0
    assert stats["p50"] == 30.0
    assert stats["p75"] == 40.0


def test_calculate_distribution_single_value():
    """Single value sample std must be 0.0."""
    values = pd.Series([42.0])
    stats = calculate_distribution_metrics(values)
    assert stats["count"] == 1
    assert stats["standard_deviation"] == 0.0
    assert stats["p25"] == 42.0
    assert stats["p95"] == 42.0


def test_calculate_dataset_distributions(sample_df):
    """Test distribution calculation for all core metrics."""
    dists = calculate_dataset_distributions(sample_df)
    assert "plays" in dists
    assert "watch_time_seconds" in dists
    assert "completion_rate" in dists
    assert "unique_viewers" in dists

    for metric, stats in dists.items():
        assert stats["count"] == len(sample_df)
        assert stats["minimum"] <= stats["maximum"]
        assert stats["p25"] <= stats["p75"]


# 5. IQR ANOMALY DETECTION TESTS
def test_detect_anomalies_iqr():
    """Test statistical anomaly detection when clear outliers exist."""
    dates = [date(2026, 8, i) for i in range(1, 11)]
    # Normal plays around 100, one massive spike at 1000 on day 10
    plays_data = [100, 105, 98, 102, 101, 99, 103, 100, 104, 1000]

    records = [
        AnalyticsExportRecord(
            content_id=1,
            date=d,
            platform=PlatformEnum.IOS,
            category_id=1,
            language_id=1,
            sessions=p + 20,
            plays=p,
            unique_viewers=p // 2,
            watch_time_seconds=p * 60,
            completed_plays=p // 2,
            completion_rate=0.50,
            buffering_events=0,
            playback_errors=0,
            quality_changes=0,
        )
        for d, p in zip(dates, plays_data)
    ]
    df = build_dataframe(records)
    anomalies = detect_anomalies_iqr(df, metrics=["plays"])

    assert len(anomalies) >= 1
    outlier = [a for a in anomalies if a["date"] == "2026-08-10"][0]
    assert outlier["metric"] == "plays"
    assert outlier["value"] == 1000.0
    assert outlier["severity"] == "HIGH"
    assert outlier["value"] > outlier["upper_bound"]


def test_detect_anomalies_constant_dataset():
    """Constant dataset (IQR == 0) must return no anomalies."""
    dates = [date(2026, 8, i) for i in range(1, 6)]
    records = [
        AnalyticsExportRecord(
            content_id=1,
            date=d,
            platform=PlatformEnum.IOS,
            category_id=1,
            language_id=1,
            sessions=100,
            plays=100,
            unique_viewers=50,
            watch_time_seconds=5000,
            completed_plays=50,
            completion_rate=0.50,
            buffering_events=0,
            playback_errors=0,
            quality_changes=0,
        )
        for d in dates
    ]
    df = build_dataframe(records)
    anomalies = detect_anomalies_iqr(df)
    assert anomalies == []


# 6. INSIGHTS HEURISTICS TESTS
def test_evaluate_content_insights(sample_df):
    """Test heuristic business insight generation."""
    insights = evaluate_content_insights(sample_df)
    assert isinstance(insights, list)

    types = [item["type"] for item in insights]
    # Check that known types are represented
    assert any(t in ["HIGH_ENGAGEMENT", "LOW_COMPLETION", "HIGH_BUFFERING", "HIGH_ERROR_RATE", "RAPID_GROWTH", "DECLINING_CONTENT"] for t in types)

    # Ensure no PII in message or payload
    for item in insights:
        assert "password" not in item["message"].lower()
        assert "email" not in item["message"].lower()
        assert "user_id" not in item


# 7. DATA PRIVACY TESTS
def test_privacy_guard_rejects_pii():
    """Validation layer must immediately raise SensitiveDataError when sensitive columns appear."""
    df = pd.DataFrame(
        {
            "content_id": [1],
            "metric_date": ["2026-08-01"],
            "platform": ["IOS"],
            "category_id": [1],
            "language_id": [1],
            "sessions": [10],
            "plays": [10],
            "unique_viewers": [5],
            "watch_time_seconds": [1000],
            "completed_plays": [5],
            "completion_rate": [0.5],
            "buffering_events": [0],
            "playback_errors": [0],
            "quality_changes": [0],
            "user_id": [9999],  # FORBIDDEN PII FIELD
        }
    )
    with pytest.raises(SensitiveDataError):
        validate_dataframe(df)


# 8. ADDITIONAL ANALYTICAL & EDGE CASE TESTS
def test_top_content_limit_boundary(sample_df):
    """Test limit clamping behavior for 1 <= N <= 100."""
    res_1 = calculate_content_performance_scores(sample_df, limit=1)
    assert len(res_1) == 1

    res_100 = calculate_content_performance_scores(sample_df, limit=100)
    assert len(res_100) == 4  # maximum available distinct content


def test_growth_partition_explicit_date(sample_df):
    """Test period growth when an explicit split_date is supplied."""
    growth = calculate_period_growth(sample_df, split_date=date(2026, 8, 3))
    assert growth["current_period_start"] == "2026-08-03"
    assert growth["previous_period_start"] == "2026-08-01"
    assert "metrics" in growth


def test_distributions_percentiles_ordering(sample_df):
    """Verify monotonic non-decreasing ordering of calculated percentiles P25 <= P50 <= P75 <= P90 <= P95."""
    dists = calculate_dataset_distributions(sample_df)
    for metric, stats in dists.items():
        assert stats["p25"] <= stats["p50"]
        assert stats["p50"] <= stats["p75"]
        assert stats["p75"] <= stats["p90"]
        assert stats["p90"] <= stats["p95"]


def test_anomaly_detection_short_dataset():
    """Datasets with fewer than 4 days must safely return an empty anomaly list."""
    dates = [date(2026, 8, 1), date(2026, 8, 2)]
    records = [
        AnalyticsExportRecord(
            content_id=1,
            date=d,
            platform=PlatformEnum.IOS,
            category_id=1,
            language_id=1,
            sessions=100,
            plays=100,
            unique_viewers=50,
            watch_time_seconds=5000,
            completed_plays=50,
            completion_rate=0.50,
            buffering_events=0,
            playback_errors=0,
            quality_changes=0,
        )
        for d in dates
    ]
    df = build_dataframe(records)
    anomalies = detect_anomalies_iqr(df)
    assert anomalies == []

