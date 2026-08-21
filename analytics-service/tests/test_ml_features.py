"""Comprehensive unit tests for ML feature engineering, temporal features, lag/rolling metrics, and leakage protection."""

from datetime import date
import numpy as np
import pandas as pd
import pytest

from app.ml.features.content_features import compute_content_share_features
from app.ml.features.engagement_features import compute_engagement_features
from app.ml.features.feature_builder import (
    BASE_FEATURE_COLUMNS,
    MLFeatureError,
    TARGET_COLUMNS,
    build_ml_features,
    compute_lag_and_rolling_features,
    compute_target_variables,
    validate_features,
)
from app.ml.features.temporal_features import compute_temporal_features
from app.ml.metadata.feature_metadata import FEATURE_REGISTRY, get_feature_registry
from app.ml.schemas.feature_schema import FEATURE_SCHEMA_VERSION, FeatureMetadataResponse
from app.processing.errors import SensitiveDataError


@pytest.fixture
def sample_ml_raw_df() -> pd.DataFrame:
    """Fixture returning a multi-day deterministic raw analytics DataFrame."""
    records = []
    dates = [
        "2026-08-01",
        "2026-08-02",
        "2026-08-03",
        "2026-08-04",
        "2026-08-05",
        "2026-08-06",
        "2026-08-07",
        "2026-08-08",
    ]
    for d in dates:
        for cid in [101, 102]:
            for plat in ["IOS", "ANDROID"]:
                records.append(
                    {
                        "date": d,
                        "content_id": cid,
                        "category_id": 1,
                        "language_id": 2,
                        "platform": plat,
                        "sessions": 100,
                        "plays": 80,
                        "unique_viewers": 70,
                        "watch_time_seconds": 2400,
                        "completed_plays": 40,
                        "completion_rate": 0.50,
                        "buffering_events": 2,
                        "playback_errors": 1,
                        "quality_changes": 3,
                    }
                )
    return pd.DataFrame(records)


def test_feature_registry_completeness():
    """Verify that all base features are formally registered in the metadata registry."""
    registry = get_feature_registry()
    registered_names = {item.name for item in registry}
    for col in BASE_FEATURE_COLUMNS:
        assert col in registered_names, f"Feature column '{col}' is missing from FEATURE_REGISTRY!"
    assert len(registry) >= len(BASE_FEATURE_COLUMNS)


def test_feature_version_declaration():
    """Verify feature schema version identifier matches contract standard."""
    assert FEATURE_SCHEMA_VERSION == "features-v1"


def test_engagement_features_calculation(sample_ml_raw_df: pd.DataFrame):
    """Test computed engagement ratios against mathematical definitions."""
    df = compute_engagement_features(sample_ml_raw_df)
    assert "plays_per_session" in df.columns
    assert "plays_per_viewer" in df.columns
    assert "watch_time_per_play" in df.columns
    assert "watch_time_per_session" in df.columns
    assert "buffering_rate" in df.columns
    assert "error_rate" in df.columns
    assert "quality_change_rate" in df.columns

    row = df.iloc[0]
    assert row["plays_per_session"] == round(80 / 100, 4)
    assert row["plays_per_viewer"] == round(80 / 70, 4)
    assert row["watch_time_per_play"] == round(2400 / 80, 2)
    assert row["watch_time_per_session"] == round(2400 / 100, 2)
    assert row["buffering_rate"] == round(2 / 80, 4)
    assert row["error_rate"] == round(1 / 80, 4)
    assert row["quality_change_rate"] == round(3 / 80, 4)


def test_engagement_safe_division():
    """Verify that zero sessions or plays yield 0.0 without NaNs or Infinities."""
    df_zero = pd.DataFrame(
        [
            {
                "date": "2026-08-01",
                "content_id": 1,
                "category_id": 1,
                "language_id": 1,
                "platform": "IOS",
                "sessions": 0,
                "plays": 0,
                "unique_viewers": 0,
                "watch_time_seconds": 0,
                "completed_plays": 0,
                "completion_rate": 0.0,
                "buffering_events": 0,
                "playback_errors": 0,
                "quality_changes": 0,
            }
        ]
    )
    res = compute_engagement_features(df_zero)
    assert res.iloc[0]["plays_per_session"] == 0.0
    assert res.iloc[0]["plays_per_viewer"] == 0.0
    assert res.iloc[0]["watch_time_per_play"] == 0.0
    assert res.iloc[0]["watch_time_per_session"] == 0.0
    assert res.iloc[0]["buffering_rate"] == 0.0
    assert res.iloc[0]["error_rate"] == 0.0
    assert res.iloc[0]["quality_change_rate"] == 0.0


def test_temporal_features_utc_extraction():
    """Verify deterministic calendar feature extraction adhering strictly to UTC date semantics."""
    df = pd.DataFrame(
        [
            {"date": "2026-08-01"},  # Saturday (day_of_week=5, is_weekend=1)
            {"date": "2026-08-03"},  # Monday (day_of_week=0, is_weekend=0)
        ]
    )
    res = compute_temporal_features(df)
    assert res.iloc[0]["day_of_week"] == 5
    assert res.iloc[0]["is_weekend"] == 1
    assert res.iloc[0]["month"] == 8
    assert res.iloc[0]["quarter"] == 3
    assert res.iloc[0]["day_of_month"] == 1

    assert res.iloc[1]["day_of_week"] == 0
    assert res.iloc[1]["is_weekend"] == 0
    assert res.iloc[1]["month"] == 8
    assert res.iloc[1]["day_of_month"] == 3


def test_content_share_features(sample_ml_raw_df: pd.DataFrame):
    """Verify that daily relative content share features sum to 1.0 per date."""
    res = compute_content_share_features(sample_ml_raw_df)
    assert "content_play_share" in res.columns
    assert "content_watch_time_share" in res.columns
    assert "content_viewer_share" in res.columns

    # Check that shares across each date sum approximately to 1.0
    day_shares = res[res["date"] == "2026-08-01"]["content_play_share"].sum()
    assert abs(day_shares - 1.0) < 1e-3


def test_lag_features_correctness():
    """Verify that 1-day lag accurately shifts prior day values partitioned by (content_id, platform)."""
    df = pd.DataFrame(
        [
            {
                "date": "2026-08-01",
                "content_id": 1,
                "platform": "IOS",
                "plays": 100,
                "watch_time_seconds": 1000,
                "unique_viewers": 50,
                "completed_plays": 40,
                "completion_rate": 0.40,
            },
            {
                "date": "2026-08-02",
                "content_id": 1,
                "platform": "IOS",
                "plays": 150,
                "watch_time_seconds": 1500,
                "unique_viewers": 75,
                "completed_plays": 60,
                "completion_rate": 0.40,
            },
        ]
    )
    res = compute_lag_and_rolling_features(df)
    # Day 1: lag is 0.0 (first observation)
    assert res.iloc[0]["plays_lag_1d"] == 0.0
    assert res.iloc[0]["plays_growth_1d"] == 1.0  # (100 - 0) / 0 => 1.0

    # Day 2: lag is Day 1 value
    assert res.iloc[1]["plays_lag_1d"] == 100.0
    assert res.iloc[1]["watch_time_lag_1d"] == 1000.0
    assert res.iloc[1]["viewers_lag_1d"] == 50.0
    assert res.iloc[1]["completion_rate_lag_1d"] == 0.40
    assert res.iloc[1]["plays_growth_1d"] == 0.50  # (150 - 100) / 100 = 0.50


def test_rolling_window_correctness():
    """Verify 7-day rolling features compute cumulative sum over historical window without future data."""
    dates = [f"2026-08-0{i}" for i in range(1, 9)]  # 8 days
    records = [
        {
            "date": d,
            "content_id": 1,
            "platform": "IOS",
            "plays": 10,
            "watch_time_seconds": 100,
            "unique_viewers": 5,
            "completed_plays": 5,
            "completion_rate": 0.5,
        }
        for d in dates
    ]
    df = pd.DataFrame(records)
    res = compute_lag_and_rolling_features(df)

    # Day 1 rolling sum = 10
    assert res.iloc[0]["plays_rolling_7d"] == 10.0
    # Day 7 rolling sum = 70 (7 * 10)
    assert res.iloc[6]["plays_rolling_7d"] == 70.0
    # Day 8 rolling sum = 70 (window of size 7, drops Day 1)
    assert res.iloc[7]["plays_rolling_7d"] == 70.0
    assert res.iloc[7]["completion_rate_rolling_7d"] == 0.50


def test_target_variables_generation():
    """Verify shift(-1) target generation creates future labels strictly partitioned by group."""
    df = pd.DataFrame(
        [
            {
                "date": "2026-08-01",
                "content_id": 1,
                "platform": "IOS",
                "plays": 100,
                "watch_time_seconds": 1000,
                "completion_rate": 0.4,
            },
            {
                "date": "2026-08-02",
                "content_id": 1,
                "platform": "IOS",
                "plays": 200,
                "watch_time_seconds": 2000,
                "completion_rate": 0.8,
            },
        ]
    )
    targets = compute_target_variables(df)
    # Day 1 target should be Day 2 value
    assert targets.iloc[0]["target_next_day_plays"] == 200.0
    assert targets.iloc[0]["target_next_day_watch_time"] == 2000.0
    assert targets.iloc[0]["target_next_day_completion_rate"] == 0.8
    # Day 2 target should be NaN (no day 3)
    assert np.isnan(targets.iloc[1]["target_next_day_plays"])


def test_target_leakage_rejection():
    """Verify validate_features raises MLFeatureError if target columns exist in input features."""
    df_with_target = pd.DataFrame(
        [
            {
                "content_id": 1,
                "completion_rate": 0.5,
                "platform": "IOS",
                "target_next_day_plays": 150.0,
            }
        ]
    )
    with pytest.raises(MLFeatureError, match="Data Leakage Violation"):
        validate_features(df_with_target, is_input_features_only=True)


def test_pii_rejection_in_features():
    """Verify validate_features raises SensitiveDataError if sensitive PII columns are present."""
    df_with_pii = pd.DataFrame(
        [
            {
                "content_id": 1,
                "platform": "IOS",
                "user_id": 9999,
            }
        ]
    )
    with pytest.raises(SensitiveDataError, match="forbidden sensitive PII"):
        validate_features(df_with_pii)


def test_infinity_rejection_in_features():
    """Verify validate_features rejects illegal infinite values."""
    df_inf = pd.DataFrame(
        [
            {
                "content_id": 1,
                "platform": "IOS",
                "plays_growth_1d": np.inf,
            }
        ]
    )
    with pytest.raises(MLFeatureError, match="illegal Infinite"):
        validate_features(df_inf)


def test_completion_rate_range_validation():
    """Verify completion_rate bounds [0.0, 1.0] are strictly validated."""
    df_invalid_cr = pd.DataFrame(
        [
            {
                "content_id": 1,
                "platform": "IOS",
                "completion_rate": 1.25,
            }
        ]
    )
    with pytest.raises(MLFeatureError, match="outside valid range"):
        validate_features(df_invalid_cr)


def test_invalid_platform_category_rejection():
    """Verify unknown platform values are rejected during validation."""
    df_invalid_plat = pd.DataFrame(
        [
            {
                "content_id": 1,
                "platform": "SMART_TV",
            }
        ]
    )
    with pytest.raises(MLFeatureError, match="Invalid platform categories"):
        validate_features(df_invalid_plat)


def test_empty_dataset_feature_building():
    """Verify build_ml_features handles empty DataFrame gracefully with expected schema columns."""
    empty_df = pd.DataFrame(
        columns=[
            "date",
            "content_id",
            "category_id",
            "language_id",
            "platform",
            "sessions",
            "plays",
            "unique_viewers",
            "watch_time_seconds",
            "completed_plays",
            "completion_rate",
            "buffering_events",
            "playback_errors",
            "quality_changes",
        ]
    )
    res = build_ml_features(empty_df, include_targets=True)
    assert res.empty
    assert "plays_lag_1d" in res.columns
    assert "target_next_day_plays" in res.columns


def test_deterministic_column_ordering(sample_ml_raw_df: pd.DataFrame):
    """Verify feature dataset preserves exact deterministic column ordering across builds."""
    df1 = build_ml_features(sample_ml_raw_df)
    df2 = build_ml_features(sample_ml_raw_df)
    assert list(df1.columns) == list(df2.columns)
    assert list(df1.columns) == ["date"] + BASE_FEATURE_COLUMNS


def test_growth_features_zero_previous_period():
    """Verify safe growth ratio evaluation when previous period is zero."""
    df = pd.DataFrame(
        [
            {"date": "2026-08-01", "content_id": 1, "platform": "IOS", "plays": 0, "watch_time_seconds": 0, "unique_viewers": 0, "completed_plays": 0, "completion_rate": 0.0},
            {"date": "2026-08-02", "content_id": 1, "platform": "IOS", "plays": 50, "watch_time_seconds": 500, "unique_viewers": 25, "completed_plays": 25, "completion_rate": 0.5},
        ]
    )
    res = compute_lag_and_rolling_features(df)
    # Day 1: previous was 0, current is 0 => growth = 0.0
    assert res.iloc[0]["plays_growth_1d"] == 0.0
    # Day 2: previous was 0, current is 50 => growth = 1.0 (100% baseline surge)
    assert res.iloc[1]["plays_growth_1d"] == 1.0
    assert res.iloc[1]["watch_time_growth_1d"] == 1.0
    assert res.iloc[1]["viewer_growth_1d"] == 1.0


def test_growth_features_negative_growth():
    """Verify growth ratio correctly represents contraction as a negative ratio."""
    df = pd.DataFrame(
        [
            {"date": "2026-08-01", "content_id": 1, "platform": "IOS", "plays": 100, "watch_time_seconds": 1000, "unique_viewers": 50, "completed_plays": 50, "completion_rate": 0.5},
            {"date": "2026-08-02", "content_id": 1, "platform": "IOS", "plays": 75, "watch_time_seconds": 750, "unique_viewers": 25, "completed_plays": 25, "completion_rate": 0.33},
        ]
    )
    res = compute_lag_and_rolling_features(df)
    # Day 2: (75 - 100) / 100 = -0.25 (-25%)
    assert res.iloc[1]["plays_growth_1d"] == -0.25
    assert res.iloc[1]["watch_time_growth_1d"] == -0.25
    assert res.iloc[1]["viewer_growth_1d"] == -0.50


def test_short_time_series_lag_rolling():
    """Verify single observation content series initializes lags to 0 and rolling to current value."""
    df = pd.DataFrame(
        [
            {"date": "2026-08-01", "content_id": 999, "platform": "WEB", "plays": 42, "watch_time_seconds": 420, "unique_viewers": 10, "completed_plays": 21, "completion_rate": 0.5},
        ]
    )
    res = compute_lag_and_rolling_features(df)
    assert len(res) == 1
    assert res.iloc[0]["plays_lag_1d"] == 0.0
    assert res.iloc[0]["plays_rolling_7d"] == 42.0
    assert res.iloc[0]["completion_rate_rolling_7d"] == 0.50


def test_constant_metrics_rolling():
    """Verify rolling calculations over unchanging values remain steady and accurate."""
    dates = [f"2026-08-{i:02d}" for i in range(1, 10)]  # 9 days
    records = [
        {"date": d, "content_id": 1, "platform": "IOS", "plays": 10, "watch_time_seconds": 100, "unique_viewers": 5, "completed_plays": 5, "completion_rate": 0.5}
        for d in dates
    ]
    df = pd.DataFrame(records)
    res = compute_lag_and_rolling_features(df)
    # Beyond day 7, rolling sum should be 70
    assert res.iloc[6]["plays_rolling_7d"] == 70.0
    assert res.iloc[7]["plays_rolling_7d"] == 70.0
    assert res.iloc[8]["plays_rolling_7d"] == 70.0


def test_feature_metadata_future_leakage_risk_false():
    """Verify all registered input features explicitly declare future_leakage_risk=False."""
    registry = get_feature_registry()
    for item in registry:
        assert item.future_leakage_risk is False, f"Feature {item.name} has illegal leakage risk flag!"


def test_weekend_flag_accuracy():
    """Verify is_weekend identifies Saturdays and Sundays accurately in UTC."""
    # 2026-08-01 is Saturday (weekend=1), 2026-08-02 is Sunday (weekend=1), 2026-08-03 is Monday (weekend=0)
    df = pd.DataFrame([{"date": "2026-08-01"}, {"date": "2026-08-02"}, {"date": "2026-08-03"}])
    res = compute_temporal_features(df)
    assert res.iloc[0]["is_weekend"] == 1
    assert res.iloc[1]["is_weekend"] == 1
    assert res.iloc[2]["is_weekend"] == 0


def test_growth_with_both_zero():
    """Verify growth ratio evaluates to 0.0 when both current and previous values are zero."""
    df = pd.DataFrame(
        [
            {"date": "2026-08-01", "content_id": 1, "platform": "IOS", "plays": 0, "watch_time_seconds": 0, "unique_viewers": 0, "completed_plays": 0, "completion_rate": 0.0},
            {"date": "2026-08-02", "content_id": 1, "platform": "IOS", "plays": 0, "watch_time_seconds": 0, "unique_viewers": 0, "completed_plays": 0, "completion_rate": 0.0},
        ]
    )
    res = compute_lag_and_rolling_features(df)
    assert res.iloc[1]["plays_growth_1d"] == 0.0
    assert res.iloc[1]["watch_time_growth_1d"] == 0.0
    assert res.iloc[1]["viewer_growth_1d"] == 0.0


def test_all_target_columns_are_in_target_constants():
    """Verify TARGET_COLUMNS list contains all next-day target variables."""
    assert "target_next_day_plays" in TARGET_COLUMNS
    assert "target_next_day_watch_time" in TARGET_COLUMNS
    assert "target_next_day_completion_rate" in TARGET_COLUMNS


def test_iso_calendar_week_calculation():
    """Verify week_of_year accurately extracts ISO calendar week in UTC."""
    df = pd.DataFrame([{"date": "2026-01-01"}, {"date": "2026-08-01"}])
    res = compute_temporal_features(df)
    assert res.iloc[0]["week_of_year"] == 1
    assert res.iloc[1]["week_of_year"] == 31  # Aug 1, 2026 is week 31


def test_negative_metric_rejection_in_validation():
    """Verify validate_features detects completion_rate below zero."""
    df = pd.DataFrame([{"content_id": 1, "platform": "IOS", "completion_rate": -0.10}])
    with pytest.raises(MLFeatureError, match="outside valid range"):
        validate_features(df)


