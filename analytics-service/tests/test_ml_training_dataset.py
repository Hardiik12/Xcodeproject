"""Tests for Phase 7.6 Checkpoint 3: Supervised Training Dataset Preparation & Leakage Protection."""

from datetime import date, timedelta
import numpy as np
import pandas as pd
import pytest

from app.ml.features.feature_builder import build_ml_features
from app.ml.models.target_builder import TARGET_COLUMN_PLAYS
from app.ml.models.training_dataset import (
    IDENTIFIER_COLUMNS,
    InsufficientTrainingDataError,
    PreparedMLDataset,
    prepare_training_dataset,
)
from app.processing.dataframe_builder import build_dataframe
from app.processing.errors import SensitiveDataError
from app.schemas.contract import AnalyticsExportRecord


@pytest.fixture
def multi_day_records() -> list[AnalyticsExportRecord]:
    """Generate 20 days of analytics records across 2 content IDs and 2 platforms."""
    records = []
    base_date = date(2026, 8, 1)

    for i in range(20):
        curr_date = base_date + timedelta(days=i)
        for cid in [1, 2]:
            for platform in ["IOS", "ANDROID"]:
                plays = 100 + (i * 10) + (cid * 5)
                records.append(
                    AnalyticsExportRecord(
                        date=curr_date,
                        content_id=cid,
                        category_id=10,
                        language_id=1,
                        platform=platform,
                        sessions=plays + 10,
                        plays=plays,
                        unique_viewers=plays - 5,
                        watch_time_seconds=plays * 120,
                        completed_plays=int(plays * 0.8),
                        completion_rate=0.8,
                        buffering_events=1,
                        playback_errors=0,
                        quality_changes=2,
                    )
                )
    return records


@pytest.fixture
def feature_df(multi_day_records) -> pd.DataFrame:
    """Build feature dataframe from multi_day_records."""
    raw_df = build_dataframe(multi_day_records)
    return build_ml_features(raw_df, include_targets=False)


def test_1_target_next_day_plays_not_in_X(feature_df: pd.DataFrame):
    """Verify target_next_day_plays is completely excluded from feature matrix X."""
    ds = prepare_training_dataset(feature_df)
    assert TARGET_COLUMN_PLAYS not in ds.X_train.columns
    assert TARGET_COLUMN_PLAYS not in ds.X_val.columns
    assert TARGET_COLUMN_PLAYS not in ds.X_test.columns


def test_2_target_next_day_watch_time_not_in_X(feature_df: pd.DataFrame):
    """Verify target_next_day_watch_time and other target columns are not in X."""
    df = feature_df.copy()
    df["target_next_day_watch_time"] = 1234.0
    ds = prepare_training_dataset(df)
    assert "target_next_day_watch_time" not in ds.X_train.columns


def test_3_rows_with_missing_target_removed(feature_df: pd.DataFrame):
    """Verify latest date records with NaN target are removed from supervised dataset."""
    raw_rows = len(feature_df)
    ds = prepare_training_dataset(feature_df)
    assert ds.total_rows < raw_rows
    # 4 series (2 content * 2 platforms), each has last date target = NaN -> 4 rows removed
    assert ds.total_rows == raw_rows - 4


def test_4_y_contains_valid_numeric_targets(feature_df: pd.DataFrame):
    """Verify target y is float64 numeric."""
    ds = prepare_training_dataset(feature_df)
    assert pd.api.types.is_numeric_dtype(ds.y_train)
    assert pd.api.types.is_numeric_dtype(ds.y_val)
    assert pd.api.types.is_numeric_dtype(ds.y_test)


def test_5_y_contains_no_nan(feature_df: pd.DataFrame):
    """Verify no NaN values exist in target y across all splits."""
    ds = prepare_training_dataset(feature_df)
    assert not ds.y_train.isna().any()
    assert not ds.y_val.isna().any()
    assert not ds.y_test.isna().any()


def test_6_y_contains_no_negative_values(feature_df: pd.DataFrame):
    """Verify target y contains no negative values."""
    ds = prepare_training_dataset(feature_df)
    assert (ds.y_train >= 0).all()
    assert (ds.y_val >= 0).all()
    assert (ds.y_test >= 0).all()


def test_7_train_dates_before_validation_dates(feature_df: pd.DataFrame):
    """Verify chronological invariant: max(train dates) < min(validation dates)."""
    ds = prepare_training_dataset(feature_df)
    max_train = pd.to_datetime(ds.X_train["date"]).max()
    min_val = pd.to_datetime(ds.X_val["date"]).min()
    assert max_train < min_val


def test_8_validation_dates_before_test_dates(feature_df: pd.DataFrame):
    """Verify chronological invariant: max(validation dates) < min(test dates)."""
    ds = prepare_training_dataset(feature_df)
    max_val = pd.to_datetime(ds.X_val["date"]).max()
    min_test = pd.to_datetime(ds.X_test["date"]).min()
    assert max_val < min_test


def test_9_content_platform_grouping_remains_valid(feature_df: pd.DataFrame):
    """Verify grouping and records remain valid after split."""
    ds = prepare_training_dataset(feature_df)
    assert set(ds.X_train["platform"].unique()).issubset({"IOS", "ANDROID", "WEB"})
    assert set(ds.X_train["content_id"].unique()) == {1, 2}


def test_10_identifiers_remain_available_in_X(feature_df: pd.DataFrame):
    """Verify content_id, date, platform are present in X for traceability."""
    ds = prepare_training_dataset(feature_df)
    for col in IDENTIFIER_COLUMNS:
        assert col in ds.X_train.columns
        assert col in ds.identifier_names


def test_11_pii_is_rejected(feature_df: pd.DataFrame):
    """Verify dataset containing forbidden PII raises SensitiveDataError."""
    df_pii = feature_df.copy()
    df_pii["email"] = "test@example.com"
    with pytest.raises(SensitiveDataError, match="forbidden PII"):
        prepare_training_dataset(df_pii)


def test_12_empty_dataset_handled_correctly():
    """Verify empty DataFrame raises InsufficientTrainingDataError."""
    empty_df = pd.DataFrame()
    with pytest.raises(InsufficientTrainingDataError, match="Dataset is empty"):
        prepare_training_dataset(empty_df)


def test_13_insufficient_dataset_handled_correctly():
    """Verify DataFrame with < 3 clean rows raises InsufficientTrainingDataError."""
    short_df = pd.DataFrame([
        {"content_id": 1, "platform": "IOS", "date": date(2026, 8, 1), "plays": 10},
        {"content_id": 1, "platform": "IOS", "date": date(2026, 8, 2), "plays": 20},
    ])
    # Target builder shifts: row 1 gets target 20, row 2 gets NaN target.
    # Rows after cleanup = 1 row < 3 -> raises InsufficientTrainingDataError
    with pytest.raises(InsufficientTrainingDataError, match="Insufficient training observations"):
        prepare_training_dataset(short_df)


def test_14_deterministic_split_produces_same_result(feature_df: pd.DataFrame):
    """Verify repeated execution produces identical dataset partitions."""
    ds1 = prepare_training_dataset(feature_df)
    ds2 = prepare_training_dataset(feature_df)

    pd.testing.assert_frame_equal(ds1.X_train, ds2.X_train)
    pd.testing.assert_series_equal(ds1.y_train, ds2.y_train)
    assert ds1.train_rows == ds2.train_rows
    assert ds1.val_rows == ds2.val_rows
    assert ds1.test_rows == ds2.test_rows


def test_15_no_random_shuffling_used(feature_df: pd.DataFrame):
    """Verify date ordering in X_train, X_val, X_test is monotonic."""
    ds = prepare_training_dataset(feature_df)
    
    train_dates = pd.to_datetime(ds.X_train["date"])
    assert train_dates.is_monotonic_increasing

    val_dates = pd.to_datetime(ds.X_val["date"])
    assert val_dates.is_monotonic_increasing


def test_16_original_dataframe_not_mutated(feature_df: pd.DataFrame):
    """Verify original input feature DataFrame is not mutated."""
    original_cols = list(feature_df.columns)
    original_len = len(feature_df)
    _ = prepare_training_dataset(feature_df)

    assert list(feature_df.columns) == original_cols
    assert len(feature_df) == original_len
