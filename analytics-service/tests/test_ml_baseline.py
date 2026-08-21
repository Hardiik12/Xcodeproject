"""Tests for Phase 7.6 Checkpoint 4: Naive Baseline Predictor and Evaluation Metrics."""

from datetime import date, timedelta
import numpy as np
import pandas as pd
import pytest

from app.ml.features.feature_builder import build_ml_features
from app.ml.models.baseline import (
    BaselinePredictor,
    calculate_mae,
    calculate_r2,
    calculate_rmse,
    evaluate_baseline,
)
from app.ml.models.training_dataset import prepare_training_dataset
from app.processing.dataframe_builder import build_dataframe
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
def prepared_dataset(multi_day_records):
    """Build prepared dataset fixture."""
    raw_df = build_dataframe(multi_day_records)
    feature_df = build_ml_features(raw_df, include_targets=False)
    return prepare_training_dataset(feature_df)


def test_1_baseline_prediction_equals_current_plays():
    """Verify baseline predictor outputs current day's plays."""
    predictor = BaselinePredictor()
    df_features = pd.DataFrame({"plays": [100.0, 200.0, 350.0]})
    preds = predictor.predict(df_features)
    np.testing.assert_array_equal(preds, np.array([100.0, 200.0, 350.0]))


def test_2_correct_target_comparison(prepared_dataset):
    """Verify baseline predictions align with validation and test rows."""
    predictor = BaselinePredictor()
    val_preds = predictor.predict(prepared_dataset.X_val)
    test_preds = predictor.predict(prepared_dataset.X_test)

    assert len(val_preds) == len(prepared_dataset.y_val)
    assert len(test_preds) == len(prepared_dataset.y_test)
    np.testing.assert_array_equal(val_preds, prepared_dataset.X_val["plays"].values)


def test_3_mae_calculation():
    """Verify MAE calculation accuracy."""
    y_true = np.array([100.0, 200.0, 300.0])
    y_pred = np.array([110.0, 190.0, 300.0])
    mae = calculate_mae(y_true, y_pred)
    assert mae == pytest.approx(6.666666666666667)


def test_4_rmse_calculation():
    """Verify RMSE calculation accuracy."""
    y_true = np.array([100.0, 200.0, 300.0])
    y_pred = np.array([110.0, 190.0, 300.0])
    rmse = calculate_rmse(y_true, y_pred)
    assert rmse == pytest.approx(8.16496580927726)


def test_5_r2_calculation():
    """Verify R^2 calculation accuracy on non-constant target."""
    y_true = np.array([100.0, 200.0, 300.0])
    y_pred = np.array([105.0, 195.0, 295.0])
    r2 = calculate_r2(y_true, y_pred)
    assert r2 is not None
    assert r2 > 0.95


def test_6_constant_target_r2_handling():
    """Verify R^2 returns None (not NaN or Inf) when y_true is constant."""
    y_true = np.array([100.0, 100.0, 100.0])
    y_pred = np.array([105.0, 95.0, 100.0])
    r2 = calculate_r2(y_true, y_pred)
    assert r2 is None


def test_7_zero_target_handling():
    """Verify zero plays in target do not produce division error or NaN metrics."""
    y_true = np.array([0.0, 0.0, 10.0])
    y_pred = np.array([5.0, 0.0, 0.0])

    mae = calculate_mae(y_true, y_pred)
    rmse = calculate_rmse(y_true, y_pred)
    assert mae == pytest.approx(5.0)
    assert rmse == pytest.approx(6.454972243679028)
    assert not np.isnan(mae)
    assert not np.isnan(rmse)


def test_8_validation_evaluation(prepared_dataset):
    """Verify baseline evaluation computes non-negative validation metrics."""
    res = evaluate_baseline(prepared_dataset)
    assert res.model == "naive_previous_day_plays"
    assert res.validation.mae >= 0
    assert res.validation.rmse >= 0


def test_9_test_evaluation(prepared_dataset):
    """Verify baseline evaluation computes non-negative test metrics independently."""
    res = evaluate_baseline(prepared_dataset)
    assert res.test.mae >= 0
    assert res.test.rmse >= 0


def test_10_no_target_leakage(prepared_dataset):
    """Verify predictor accesses ONLY 'plays' column and does not read target columns."""
    predictor = BaselinePredictor()
    X_sample = prepared_dataset.X_val.copy()

    # Confirm target is not present in X_sample
    assert "target_next_day_plays" not in X_sample.columns

    preds = predictor.predict(X_sample)
    assert len(preds) == len(X_sample)


def test_11_deterministic_results(prepared_dataset):
    """Verify repeated baseline evaluation produces identical metric scores."""
    res1 = evaluate_baseline(prepared_dataset)
    res2 = evaluate_baseline(prepared_dataset)

    assert res1.validation.mae == res2.validation.mae
    assert res1.validation.rmse == res2.validation.rmse
    assert res1.validation.r2 == res2.validation.r2
    assert res1.test.mae == res2.test.mae
    assert res1.test.rmse == res2.test.rmse
    assert res1.test.r2 == res2.test.r2


def test_12_empty_input_handling():
    """Verify empty DataFrame returns empty NumPy array."""
    predictor = BaselinePredictor()
    df_empty = pd.DataFrame()
    preds = predictor.predict(df_empty)
    assert len(preds) == 0


def test_13_insufficient_input_handling():
    """Verify DataFrame missing 'plays' column raises ValueError."""
    predictor = BaselinePredictor()
    df_invalid = pd.DataFrame({"sessions": [10, 20]})
    with pytest.raises(ValueError, match="must contain 'plays' column"):
        predictor.predict(df_invalid)


def test_14_non_negative_mae_rmse_invariants():
    """Verify MAE and RMSE are strictly >= 0."""
    y_true = np.array([50.0, 60.0])
    y_pred = np.array([50.0, 60.0])
    mae = calculate_mae(y_true, y_pred)
    rmse = calculate_rmse(y_true, y_pred)

    assert mae == 0.0
    assert rmse == 0.0
