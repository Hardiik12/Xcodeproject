"""Tests for Phase 7.6 Checkpoint 6: Random Forest Model Diagnostics."""

from datetime import date, timedelta
import numpy as np
import pandas as pd
import pytest

from app.ml.features.feature_builder import build_ml_features
from app.ml.models.model_diagnostics import (
    analyze_prediction_bias,
    analyze_residuals,
    analyze_target_distributions,
    calculate_feature_target_correlations,
    calculate_grouped_metrics,
    calculate_residuals,
    run_model_diagnostics,
)
from app.ml.models.model_trainer import ModelTrainer
from app.ml.models.training_dataset import PreparedMLDataset, prepare_training_dataset
from app.processing.dataframe_builder import build_dataframe
from app.processing.errors import SensitiveDataError
from app.schemas.contract import AnalyticsExportRecord


@pytest.fixture
def multi_day_records() -> list[AnalyticsExportRecord]:
    """Generate 25 days of analytics records across 2 content IDs and 2 platforms."""
    records = []
    base_date = date(2026, 8, 1)

    for i in range(25):
        curr_date = base_date + timedelta(days=i)
        for cid in [1, 2]:
            for platform in ["IOS", "ANDROID"]:
                plays = 100 + (i * 12) + (cid * 7)
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
def prepared_dataset(multi_day_records) -> PreparedMLDataset:
    """Build prepared dataset fixture."""
    raw_df = build_dataframe(multi_day_records)
    feature_df = build_ml_features(raw_df, include_targets=False)
    return prepare_training_dataset(feature_df)


def test_1_residual_calculation():
    """Verify residual = actual - prediction."""
    y_true = np.array([100.0, 200.0])
    y_pred = np.array([90.0, 210.0])
    df_res = calculate_residuals(y_true, y_pred)

    np.testing.assert_array_equal(df_res["residual"].values, np.array([10.0, -10.0]))


def test_2_absolute_error_calculation():
    """Verify absolute error calculation."""
    y_true = np.array([100.0, 200.0])
    y_pred = np.array([90.0, 210.0])
    df_res = calculate_residuals(y_true, y_pred)

    np.testing.assert_array_equal(df_res["absolute_error"].values, np.array([10.0, 10.0]))


def test_3_squared_error_calculation():
    """Verify squared error calculation."""
    y_true = np.array([100.0, 200.0])
    y_pred = np.array([90.0, 210.0])
    df_res = calculate_residuals(y_true, y_pred)

    np.testing.assert_array_equal(df_res["squared_error"].values, np.array([100.0, 100.0]))


def test_4_mae_in_residuals():
    """Verify MAE calculation in residual summary."""
    y_true = np.array([100.0, 200.0])
    y_pred = np.array([90.0, 210.0])
    df_res = calculate_residuals(y_true, y_pred)
    summary = analyze_residuals(df_res)

    assert summary.mae == pytest.approx(10.0)


def test_5_rmse_in_residuals():
    """Verify RMSE calculation in residual summary."""
    y_true = np.array([100.0, 200.0])
    y_pred = np.array([90.0, 210.0])
    df_res = calculate_residuals(y_true, y_pred)
    summary = analyze_residuals(df_res)

    assert summary.rmse == pytest.approx(10.0)


def test_6_r2_in_residuals():
    """Verify R^2 calculation in residual summary."""
    y_true = np.array([100.0, 200.0, 300.0])
    y_pred = np.array([100.0, 200.0, 300.0])
    df_res = calculate_residuals(y_true, y_pred)
    summary = analyze_residuals(df_res)

    assert summary.r2 == pytest.approx(1.0)


def test_7_directional_bias():
    """Verify directional bias interpretations (UNDERPREDICTION, OVERPREDICTION, NEUTRAL)."""
    # Underprediction (actual > pred, mean residual > 0.5)
    df_under = calculate_residuals(np.array([100.0, 200.0]), np.array([80.0, 180.0]))
    assert analyze_residuals(df_under).directional_bias == "UNDERPREDICTION"

    # Overprediction (actual < pred, mean residual < -0.5)
    df_over = calculate_residuals(np.array([100.0, 200.0]), np.array([120.0, 220.0]))
    assert analyze_residuals(df_over).directional_bias == "OVERPREDICTION"

    # Neutral
    df_neutral = calculate_residuals(np.array([100.0, 200.0]), np.array([100.0, 200.0]))
    assert analyze_residuals(df_neutral).directional_bias == "NEUTRAL"


def test_8_prediction_bias():
    """Verify mean prediction bias calculation."""
    y_true = np.array([100.0, 200.0])
    y_pred = np.array([110.0, 210.0])
    bias_summary = analyze_prediction_bias(y_true, y_pred)

    assert bias_summary.mean_actual == 150.0
    assert bias_summary.mean_prediction == 160.0
    assert bias_summary.prediction_bias == 10.0


def test_9_prediction_compression_status():
    """Verify prediction compression detection when prediction range << actual range."""
    # Actual range = 1000 - 100 = 900. Pred range = 520 - 500 = 20 (< 0.5 * 900)
    y_true = np.array([100.0, 500.0, 1000.0])
    y_pred = np.array([500.0, 510.0, 520.0])
    bias_summary = analyze_prediction_bias(y_true, y_pred)

    assert bias_summary.compression_status == "PREDICTION COMPRESSION DETECTED"


def test_10_content_level_metrics():
    """Verify error metrics grouped by content_id."""
    df_res = pd.DataFrame(
        {
            "actual": [100.0, 200.0, 300.0, 400.0],
            "prediction": [90.0, 190.0, 300.0, 410.0],
            "residual": [10.0, 10.0, 0.0, -10.0],
            "content_id": [1, 1, 2, 2],
        }
    )
    c_metrics = calculate_grouped_metrics(df_res, "content_id")
    assert len(c_metrics) == 2
    assert c_metrics[0].group_key == "1"
    assert c_metrics[0].sample_count == 2
    assert c_metrics[0].mae == 10.0


def test_11_platform_level_metrics():
    """Verify error metrics grouped by platform."""
    df_res = pd.DataFrame(
        {
            "actual": [100.0, 200.0],
            "prediction": [95.0, 195.0],
            "residual": [5.0, 5.0],
            "platform": ["IOS", "ANDROID"],
        }
    )
    p_metrics = calculate_grouped_metrics(df_res, "platform")
    assert len(p_metrics) == 2
    assert set(m.group_key for m in p_metrics) == {"IOS", "ANDROID"}


def test_12_date_level_metrics():
    """Verify error metrics grouped by date."""
    df_res = pd.DataFrame(
        {
            "actual": [100.0, 200.0],
            "prediction": [90.0, 190.0],
            "residual": [10.0, 10.0],
            "date": ["2026-08-01", "2026-08-02"],
        }
    )
    d_metrics = calculate_grouped_metrics(df_res, "date")
    assert len(d_metrics) == 2
    assert d_metrics[0].group_key == "2026-08-01"


def test_13_split_target_distribution(prepared_dataset: PreparedMLDataset):
    """Verify target distribution statistics across Train, Validation, and Test."""
    stats = analyze_target_distributions(prepared_dataset)
    assert "Train" in stats
    assert "Validation" in stats
    assert "Test" in stats
    assert stats["Train"].count > 0
    assert stats["Validation"].count > 0
    assert stats["Test"].count > 0


def test_14_baseline_comparison_diagnostics(prepared_dataset: PreparedMLDataset):
    """Verify run_model_diagnostics executes full diagnostic pipeline."""
    trainer = ModelTrainer()
    train_res = trainer.train_and_evaluate(prepared_dataset)

    # Predict using trained model
    X_val_proc = train_res.pipeline_instance.transform(prepared_dataset.X_val)
    X_te_proc = train_res.pipeline_instance.transform(prepared_dataset.X_test)
    val_preds = train_res.model_instance.predict(X_val_proc)
    test_preds = train_res.model_instance.predict(X_te_proc)

    diag = run_model_diagnostics(prepared_dataset, val_preds, test_preds)

    assert diag.residual_summary_val is not None
    assert diag.residual_summary_test is not None
    assert "naive_previous_day_plays" in diag.baseline_diagnosis


def test_15_deterministic_diagnostics(prepared_dataset: PreparedMLDataset):
    """Verify diagnostics calculations are strictly deterministic."""
    val_preds = np.full(len(prepared_dataset.y_val), 150.0)
    test_preds = np.full(len(prepared_dataset.y_test), 200.0)

    d1 = run_model_diagnostics(prepared_dataset, val_preds, test_preds)
    d2 = run_model_diagnostics(prepared_dataset, val_preds, test_preds)

    assert d1.residual_summary_val.mae == d2.residual_summary_val.mae
    assert d1.bias_summary_val.prediction_bias == d2.bias_summary_val.prediction_bias


def test_16_empty_input_handling():
    """Verify calculate_residuals with empty inputs handles empty DataFrame gracefully."""
    df_empty = calculate_residuals(np.array([]), np.array([]))
    assert df_empty.empty
    summary = analyze_residuals(df_empty)
    assert summary.mae == 0.0
    assert summary.directional_bias == "NEUTRAL"


def test_17_mismatched_array_lengths_raises_value_error():
    """Verify mismatched array lengths raise ValueError."""
    with pytest.raises(ValueError, match="Array length mismatch"):
        calculate_residuals(np.array([100.0]), np.array([100.0, 200.0]))


def test_18_feature_target_correlations(prepared_dataset: PreparedMLDataset):
    """Verify top feature correlations with target are computed."""
    corrs = calculate_feature_target_correlations(prepared_dataset.X_train, prepared_dataset.y_train)
    assert len(corrs) > 0
    assert corrs[0].abs_correlation >= corrs[-1].abs_correlation


def test_19_pii_rejection_in_diagnostics(prepared_dataset: PreparedMLDataset):
    """Verify SensitiveDataError is raised if PII columns exist in X_train."""
    ds = prepared_dataset
    ds.X_train["email"] = "user@example.com"
    val_preds = np.ones(len(ds.y_val))
    test_preds = np.ones(len(ds.y_test))

    with pytest.raises(SensitiveDataError, match="PII violation"):
        run_model_diagnostics(ds, val_preds, test_preds)


def test_20_no_mutation_of_source_dataframe(prepared_dataset: PreparedMLDataset):
    """Verify source feature DataFrame is not mutated during diagnostics."""
    cols_before = list(prepared_dataset.X_val.columns)
    val_preds = np.ones(len(prepared_dataset.y_val))
    test_preds = np.ones(len(prepared_dataset.y_test))

    _ = run_model_diagnostics(prepared_dataset, val_preds, test_preds)
    assert list(prepared_dataset.X_val.columns) == cols_before
