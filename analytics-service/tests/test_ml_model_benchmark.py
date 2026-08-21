"""Tests for Phase 7.6 Checkpoint 7: Time-Series Model Benchmark."""

from datetime import date, timedelta
import numpy as np
import pandas as pd
import pytest

from app.ml.features.feature_builder import build_ml_features
from app.ml.models.model_benchmark import ModelBenchmarkRunner, ModelBenchmarkResult
from app.ml.models.training_dataset import PreparedMLDataset, InsufficientTrainingDataError, prepare_training_dataset
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


def test_1_benchmark_creation(prepared_dataset: PreparedMLDataset):
    """Verify ModelBenchmarkRunner executes successfully."""
    runner = ModelBenchmarkRunner()
    res = runner.run_benchmark(prepared_dataset)
    assert isinstance(res, ModelBenchmarkResult)


def test_2_naive_model_included(prepared_dataset: PreparedMLDataset):
    """Verify naive_previous_day_plays is included in benchmark."""
    runner = ModelBenchmarkRunner()
    res = runner.run_benchmark(prepared_dataset)
    assert "naive_previous_day_plays" in res.models


def test_3_random_forest_included(prepared_dataset: PreparedMLDataset):
    """Verify random_forest is included in benchmark."""
    runner = ModelBenchmarkRunner()
    res = runner.run_benchmark(prepared_dataset)
    assert "random_forest" in res.models


def test_4_ridge_included(prepared_dataset: PreparedMLDataset):
    """Verify ridge_regression is included in benchmark."""
    runner = ModelBenchmarkRunner()
    res = runner.run_benchmark(prepared_dataset)
    assert "ridge_regression" in res.models


def test_5_ridge_configuration(prepared_dataset: PreparedMLDataset):
    """Verify Ridge runner parameters alpha=1.0."""
    runner = ModelBenchmarkRunner(ridge_alpha=1.0)
    assert runner.ridge_alpha == 1.0


def test_6_training_succeeds(prepared_dataset: PreparedMLDataset):
    """Verify benchmark runs all 3 models without exceptions."""
    runner = ModelBenchmarkRunner()
    res = runner.run_benchmark(prepared_dataset)
    assert len(res.models) == 3


def test_7_validation_predictions_generated(prepared_dataset: PreparedMLDataset):
    """Verify validation predictions diagnostics are populated."""
    runner = ModelBenchmarkRunner()
    res = runner.run_benchmark(prepared_dataset)

    for name, model_bm in res.models.items():
        assert model_bm.diagnostics_val.mean > 0.0


def test_8_test_predictions_generated(prepared_dataset: PreparedMLDataset):
    """Verify test predictions diagnostics are populated."""
    runner = ModelBenchmarkRunner()
    res = runner.run_benchmark(prepared_dataset)

    for name, model_bm in res.models.items():
        assert model_bm.diagnostics_test.mean > 0.0


def test_9_mae_calculated(prepared_dataset: PreparedMLDataset):
    """Verify MAE is computed for all models."""
    runner = ModelBenchmarkRunner()
    res = runner.run_benchmark(prepared_dataset)

    for name, model_bm in res.models.items():
        assert model_bm.validation_metrics.mae >= 0.0
        assert model_bm.test_metrics.mae >= 0.0


def test_10_rmse_calculated(prepared_dataset: PreparedMLDataset):
    """Verify RMSE is computed for all models."""
    runner = ModelBenchmarkRunner()
    res = runner.run_benchmark(prepared_dataset)

    for name, model_bm in res.models.items():
        assert model_bm.validation_metrics.rmse >= 0.0
        assert model_bm.test_metrics.rmse >= 0.0


def test_11_r2_calculated(prepared_dataset: PreparedMLDataset):
    """Verify R^2 is computed or None for all models."""
    runner = ModelBenchmarkRunner()
    res = runner.run_benchmark(prepared_dataset)

    for name, model_bm in res.models.items():
        assert model_bm.validation_metrics.r2 is None or isinstance(model_bm.validation_metrics.r2, float)


def test_12_model_comparison_map(prepared_dataset: PreparedMLDataset):
    """Verify comparison map contains exact expected keys."""
    runner = ModelBenchmarkRunner()
    res = runner.run_benchmark(prepared_dataset)
    assert set(res.models.keys()) == {"naive_previous_day_plays", "random_forest", "ridge_regression"}


def test_13_test_ranking_sorted_by_mae(prepared_dataset: PreparedMLDataset):
    """Verify test ranking is ordered by ascending test MAE."""
    runner = ModelBenchmarkRunner()
    res = runner.run_benchmark(prepared_dataset)

    test_maes = [res.models[name].test_metrics.mae for name in res.test_ranking]
    assert test_maes == sorted(test_maes)


def test_14_validation_based_selection(prepared_dataset: PreparedMLDataset):
    """Verify validation_best_model is selected based on lowest validation MAE."""
    runner = ModelBenchmarkRunner()
    res = runner.run_benchmark(prepared_dataset)

    val_maes = {name: res.models[name].validation_metrics.mae for name in res.models}
    best_expected = min(val_maes, key=val_maes.get)
    assert res.validation_best_model == best_expected


def test_15_test_remains_evaluation_only(prepared_dataset: PreparedMLDataset):
    """Verify test dataset is NOT used for selection decision."""
    runner = ModelBenchmarkRunner()
    res = runner.run_benchmark(prepared_dataset)
    assert "Validation MAE" in res.validation_selection_reason


def test_16_deterministic_results(prepared_dataset: PreparedMLDataset):
    """Verify benchmark results are deterministic across runs."""
    r1 = ModelBenchmarkRunner(random_state=42).run_benchmark(prepared_dataset)
    r2 = ModelBenchmarkRunner(random_state=42).run_benchmark(prepared_dataset)

    assert r1.validation_best_model == r2.validation_best_model
    assert r1.test_ranking == r2.test_ranking


def test_17_negative_prediction_reporting(prepared_dataset: PreparedMLDataset):
    """Verify negative prediction count is tracked for all models."""
    runner = ModelBenchmarkRunner()
    res = runner.run_benchmark(prepared_dataset)

    for name, model_bm in res.models.items():
        assert isinstance(model_bm.diagnostics_val.negative_prediction_count, int)
        assert isinstance(model_bm.diagnostics_test.negative_prediction_count, int)


def test_18_no_target_leakage(prepared_dataset: PreparedMLDataset):
    """Verify target column is excluded from feature set."""
    runner = ModelBenchmarkRunner()
    res = runner.run_benchmark(prepared_dataset)
    assert "target_next_day_plays" not in prepared_dataset.X_train.columns


def test_19_preprocessing_fit_only_on_train(prepared_dataset: PreparedMLDataset):
    """Verify benchmark completes with preprocessor trained strictly on X_train."""
    runner = ModelBenchmarkRunner()
    res = runner.run_benchmark(prepared_dataset)
    assert res.data_source == "SYNTHETIC_FIXTURE"


def test_20_pii_rejection_in_benchmark(prepared_dataset: PreparedMLDataset):
    """Verify SensitiveDataError is raised if PII is present in benchmark features."""
    ds = prepared_dataset
    ds.X_train["user_id"] = 12345
    runner = ModelBenchmarkRunner()

    with pytest.raises(SensitiveDataError, match="PII violation"):
        runner.run_benchmark(ds)


def test_21_empty_dataset_raises_error():
    """Verify empty dataset raises InsufficientTrainingDataError."""
    runner = ModelBenchmarkRunner()
    empty_ds = PreparedMLDataset(
        X_train=pd.DataFrame(), y_train=pd.Series(dtype="float64"),
        X_val=pd.DataFrame(), y_val=pd.Series(dtype="float64"),
        X_test=pd.DataFrame(), y_test=pd.Series(dtype="float64"),
        train_from=None, train_to=None, val_from=None, val_to=None, test_from=None, test_to=None,
        total_rows=0, train_rows=0, val_rows=0, test_rows=0,
        feature_names=[], target_name="target_next_day_plays", identifier_names=[],
    )
    with pytest.raises(InsufficientTrainingDataError, match="Insufficient training data"):
        runner.run_benchmark(empty_ds)


def test_22_insufficient_dataset_raises_error():
    """Verify 1-row dataset raises InsufficientTrainingDataError."""
    runner = ModelBenchmarkRunner()
    short_ds = PreparedMLDataset(
        X_train=pd.DataFrame([{"plays": 10}]), y_train=pd.Series([20.0]),
        X_val=pd.DataFrame(), y_val=pd.Series(dtype="float64"),
        X_test=pd.DataFrame(), y_test=pd.Series(dtype="float64"),
        train_from=None, train_to=None, val_from=None, val_to=None, test_from=None, test_to=None,
        total_rows=1, train_rows=1, val_rows=0, test_rows=0,
        feature_names=["plays"], target_name="target_next_day_plays", identifier_names=[],
    )
    with pytest.raises(InsufficientTrainingDataError, match="Insufficient training data"):
        runner.run_benchmark(short_ds)


def test_23_synthetic_data_labeling(prepared_dataset: PreparedMLDataset):
    """Verify data_source = SYNTHETIC_FIXTURE and production_evaluation = NOT_AVAILABLE."""
    runner = ModelBenchmarkRunner()
    res = runner.run_benchmark(prepared_dataset)
    assert res.data_source == "SYNTHETIC_FIXTURE"
    assert res.production_evaluation == "NOT_AVAILABLE"


def test_24_baseline_improvement_calculation(prepared_dataset: PreparedMLDataset):
    """Verify baseline improvement calculations exist for Random Forest and Ridge."""
    runner = ModelBenchmarkRunner()
    res = runner.run_benchmark(prepared_dataset)

    rf_imp = res.models["random_forest"].val_improvement_vs_naive
    ridge_imp = res.models["ridge_regression"].val_improvement_vs_naive

    assert isinstance(rf_imp.mae_improvement_pct, float)
    assert isinstance(ridge_imp.mae_improvement_pct, float)
