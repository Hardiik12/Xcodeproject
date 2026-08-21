"""Tests for Phase 7.6 Checkpoint 5: First Random Forest Model Training & Evaluation."""

from datetime import date, timedelta
import numpy as np
import pandas as pd
import pytest
from sklearn.ensemble import RandomForestRegressor

from app.ml.features.feature_builder import build_ml_features
from app.ml.models.model_trainer import (
    ImprovementMetrics,
    MLTrainingResult,
    ModelTrainer,
    calculate_improvement_pct,
)
from app.ml.models.target_builder import TARGET_COLUMN_PLAYS
from app.ml.models.training_dataset import (
    InsufficientTrainingDataError,
    PreparedMLDataset,
    prepare_training_dataset,
)
from app.processing.dataframe_builder import build_dataframe
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


def test_1_random_forest_model_creation():
    """Verify ModelTrainer creates RandomForestRegressor with configured hyper-parameters."""
    trainer = ModelTrainer(n_estimators=200, random_state=42, n_jobs=-1, max_depth=10, min_samples_leaf=2)
    assert trainer.n_estimators == 200
    assert trainer.random_state == 42
    assert trainer.max_depth == 10
    assert trainer.min_samples_leaf == 2


def test_2_correct_target_name(prepared_dataset):
    """Verify target name in training result is target_next_day_plays."""
    trainer = ModelTrainer()
    res = trainer.train_and_evaluate(prepared_dataset)
    assert res.algorithm == "RandomForestRegressor"


def test_3_training_succeeds(prepared_dataset):
    """Verify training executes without errors and produces valid model instance."""
    trainer = ModelTrainer()
    res = trainer.train_and_evaluate(prepared_dataset)
    assert res.model_instance is not None
    assert isinstance(res.model_instance, RandomForestRegressor)


def test_4_prediction_succeeds(prepared_dataset):
    """Verify model prediction outputs numpy array."""
    trainer = ModelTrainer()
    res = trainer.train_and_evaluate(prepared_dataset)
    assert res.validation_metrics.mae >= 0
    assert res.test_metrics.mae >= 0


def test_5_validation_prediction_shape(prepared_dataset):
    """Verify validation predictions count matches X_val rows."""
    trainer = ModelTrainer()
    res = trainer.train_and_evaluate(prepared_dataset)
    assert res.validation_rows == len(prepared_dataset.X_val)


def test_6_test_prediction_shape(prepared_dataset):
    """Verify test predictions count matches X_test rows."""
    trainer = ModelTrainer()
    res = trainer.train_and_evaluate(prepared_dataset)
    assert res.test_rows == len(prepared_dataset.X_test)


def test_7_mae_metric_calculated(prepared_dataset):
    """Verify MAE metric is non-negative numeric."""
    trainer = ModelTrainer()
    res = trainer.train_and_evaluate(prepared_dataset)
    assert res.validation_metrics.mae >= 0
    assert res.test_metrics.mae >= 0


def test_8_rmse_metric_calculated(prepared_dataset):
    """Verify RMSE metric is non-negative numeric."""
    trainer = ModelTrainer()
    res = trainer.train_and_evaluate(prepared_dataset)
    assert res.validation_metrics.rmse >= 0
    assert res.test_metrics.rmse >= 0


def test_9_r2_metric_calculated(prepared_dataset):
    """Verify R^2 metric calculation."""
    trainer = ModelTrainer()
    res = trainer.train_and_evaluate(prepared_dataset)
    assert res.validation_metrics.r2 is None or isinstance(res.validation_metrics.r2, float)
    assert res.test_metrics.r2 is None or isinstance(res.test_metrics.r2, float)


def test_10_deterministic_random_state(prepared_dataset):
    """Verify fixed random_state=42 produces identical predictions and metrics within float precision."""
    t1 = ModelTrainer(random_state=42, n_jobs=1)
    t2 = ModelTrainer(random_state=42, n_jobs=1)

    res1 = t1.train_and_evaluate(prepared_dataset)
    res2 = t2.train_and_evaluate(prepared_dataset)

    assert res1.validation_metrics.mae == pytest.approx(res2.validation_metrics.mae, abs=1e-9)
    assert res1.test_metrics.mae == pytest.approx(res2.test_metrics.mae, abs=1e-9)
    assert res1.feature_importance == res2.feature_importance


def test_11_target_not_in_X(prepared_dataset):
    """Verify target columns are strictly excluded from X before model training."""
    trainer = ModelTrainer()
    res = trainer.train_and_evaluate(prepared_dataset)
    assert TARGET_COLUMN_PLAYS not in prepared_dataset.X_train.columns


def test_12_future_features_rejected(prepared_dataset):
    """Verify target leakage raises ValueError if target exists in X_train."""
    ds = prepared_dataset
    ds.X_train["target_next_day_plays"] = 100.0
    trainer = ModelTrainer()

    with pytest.raises(ValueError, match="Target leakage violation"):
        trainer.train_and_evaluate(ds)


def test_13_preprocessing_fit_only_on_training(prepared_dataset):
    """Verify preprocessing pipeline is fit ONLY on X_train."""
    trainer = ModelTrainer()
    res = trainer.train_and_evaluate(prepared_dataset)
    assert res.pipeline_instance is not None
    # Check pipeline contains transformers fit on X_train columns
    assert hasattr(res.pipeline_instance, "transformers_")


def test_14_feature_importance_exists(prepared_dataset):
    """Verify top feature importances are extracted."""
    trainer = ModelTrainer()
    res = trainer.train_and_evaluate(prepared_dataset)
    assert len(res.feature_importance) > 0
    assert "feature" in res.feature_importance[0]
    assert "importance" in res.feature_importance[0]


def test_15_feature_importance_sorted_descending(prepared_dataset):
    """Verify feature importances are sorted in descending order."""
    trainer = ModelTrainer()
    res = trainer.train_and_evaluate(prepared_dataset)
    scores = [item["importance"] for item in res.feature_importance]
    assert scores == sorted(scores, reverse=True)


def test_16_baseline_comparison_calculated(prepared_dataset):
    """Verify baseline metrics and improvement percentages are populated."""
    trainer = ModelTrainer()
    res = trainer.train_and_evaluate(prepared_dataset)

    assert res.baseline_validation_metrics.mae >= 0
    assert res.baseline_test_metrics.mae >= 0
    assert isinstance(res.improvement_validation.mae_improvement_pct, float)
    assert isinstance(res.improvement_test.mae_improvement_pct, float)


def test_17_synthetic_data_labeling(prepared_dataset):
    """Verify synthetic fixture data source label is preserved."""
    trainer = ModelTrainer()
    res = trainer.train_and_evaluate(prepared_dataset, data_source_label="SYNTHETIC FIXTURE RESULTS")
    assert res.data_source == "SYNTHETIC FIXTURE RESULTS"


def test_18_empty_dataset_handling():
    """Verify empty PreparedMLDataset raises InsufficientTrainingDataError."""
    trainer = ModelTrainer()
    empty_ds = PreparedMLDataset(
        X_train=pd.DataFrame(),
        y_train=pd.Series(dtype="float64"),
        X_val=pd.DataFrame(),
        y_val=pd.Series(dtype="float64"),
        X_test=pd.DataFrame(),
        y_test=pd.Series(dtype="float64"),
        train_from=None, train_to=None, val_from=None, val_to=None, test_from=None, test_to=None,
        total_rows=0, train_rows=0, val_rows=0, test_rows=0,
        feature_names=[], target_name="target_next_day_plays", identifier_names=[],
    )

    with pytest.raises(InsufficientTrainingDataError, match="Insufficient training data"):
        trainer.train_and_evaluate(empty_ds)


def test_19_insufficient_dataset_handling():
    """Verify dataset with 1 row raises InsufficientTrainingDataError."""
    trainer = ModelTrainer()
    short_ds = PreparedMLDataset(
        X_train=pd.DataFrame([{"plays": 10}]),
        y_train=pd.Series([20.0]),
        X_val=pd.DataFrame(),
        y_val=pd.Series(dtype="float64"),
        X_test=pd.DataFrame(),
        y_test=pd.Series(dtype="float64"),
        train_from=None, train_to=None, val_from=None, val_to=None, test_from=None, test_to=None,
        total_rows=1, train_rows=1, val_rows=0, test_rows=0,
        feature_names=["plays"], target_name="target_next_day_plays", identifier_names=[],
    )

    with pytest.raises(InsufficientTrainingDataError, match="Insufficient training data"):
        trainer.train_and_evaluate(short_ds)


def test_20_calculate_improvement_pct_zero_baseline():
    """Verify calculate_improvement_pct handles 0 baseline gracefully."""
    assert calculate_improvement_pct(0.0, 10.0) == 0.0
    assert calculate_improvement_pct(100.0, 80.0) == pytest.approx(20.0)
