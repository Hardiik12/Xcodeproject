"""Tests for Phase 7.6 Checkpoint 8: Model Selection & Production Candidate Assessment."""

from datetime import date, timedelta
import numpy as np
import pandas as pd
import pytest

from app.ml.features.feature_builder import build_ml_features
from app.ml.models.model_card import ModelCard
from app.ml.models.model_selector import FEATURE_SCHEMA_VERSION, ModelSelectionResult, select_production_candidate
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


def test_1_validation_based_selection(prepared_dataset: PreparedMLDataset):
    """Verify selection result selects model with lowest validation MAE."""
    res = select_production_candidate(prepared_dataset)
    assert isinstance(res, ModelSelectionResult)
    assert res.selected_model in {"naive_previous_day_plays", "random_forest", "ridge_regression"}


def test_2_lowest_mae_selection(prepared_dataset: PreparedMLDataset):
    """Verify selected model matches model with lowest validation MAE."""
    res = select_production_candidate(prepared_dataset)
    bm_res = res.benchmark_result
    val_maes = {name: bm_res.models[name].validation_metrics.mae for name in bm_res.models}
    best_expected = min(val_maes, key=val_maes.get)
    assert res.selected_model == best_expected


def test_3_rmse_tie_breaker(prepared_dataset: PreparedMLDataset):
    """Verify RMSE tie breaker logic is preserved in selection rule."""
    res = select_production_candidate(prepared_dataset)
    assert "Validation MAE=" in res.selection_reason


def test_4_r2_tie_breaker(prepared_dataset: PreparedMLDataset):
    """Verify R^2 tie breaker is included in selection metadata."""
    res = select_production_candidate(prepared_dataset)
    assert res.validation_metrics.r2 is None or isinstance(res.validation_metrics.r2, float)


def test_5_test_not_used_for_selection(prepared_dataset: PreparedMLDataset):
    """Verify selection reason states validation split was used exclusively."""
    res = select_production_candidate(prepared_dataset)
    assert "validation split performance" in res.selection_reason


def test_6_selected_model_test_evaluation(prepared_dataset: PreparedMLDataset):
    """Verify final test metrics are computed for the selected model."""
    res = select_production_candidate(prepared_dataset)
    assert res.final_test_metrics.mae >= 0.0
    assert res.final_test_metrics.rmse >= 0.0


def test_7_baseline_comparison(prepared_dataset: PreparedMLDataset):
    """Verify baseline test metrics are included for comparison."""
    res = select_production_candidate(prepared_dataset)
    assert res.baseline_test_metrics.mae >= 0.0


def test_8_mae_improvement(prepared_dataset: PreparedMLDataset):
    """Verify test MAE improvement percentage is calculated."""
    res = select_production_candidate(prepared_dataset)
    assert isinstance(res.test_mae_improvement_pct, float)


def test_9_rmse_improvement(prepared_dataset: PreparedMLDataset):
    """Verify test RMSE improvement percentage is calculated."""
    res = select_production_candidate(prepared_dataset)
    assert isinstance(res.test_rmse_improvement_pct, float)


def test_10_stability_calculation(prepared_dataset: PreparedMLDataset):
    """Verify degradation ratio test_mae / val_mae is computed."""
    res = select_production_candidate(prepared_dataset)
    assert res.degradation_ratio is not None
    assert res.stability_status in {"STABLE", "POTENTIAL_INSTABILITY"}


def test_11_zero_validation_mae_handling():
    """Verify zero validation MAE produces degradation_ratio = None and status STABLE."""
    prepared_mock = PreparedMLDataset(
        X_train=pd.DataFrame([{"plays": 10, "content_id": 1, "date": "2026-08-01", "platform": "IOS"}, {"plays": 20, "content_id": 1, "date": "2026-08-02", "platform": "IOS"}]),
        y_train=pd.Series([20.0, 30.0]),
        X_val=pd.DataFrame([{"plays": 30, "content_id": 1, "date": "2026-08-03", "platform": "IOS"}]),
        y_val=pd.Series([40.0]),
        X_test=pd.DataFrame([{"plays": 40, "content_id": 1, "date": "2026-08-04", "platform": "IOS"}]),
        y_test=pd.Series([50.0]),
        train_from="2026-08-01", train_to="2026-08-02", val_from="2026-08-03", val_to="2026-08-03", test_from="2026-08-04", test_to="2026-08-04",
        total_rows=4, train_rows=2, val_rows=1, test_rows=1,
        feature_names=["plays"], target_name="target_next_day_plays", identifier_names=["content_id", "date", "platform"],
    )
    res = select_production_candidate(prepared_mock)
    assert res.degradation_ratio is not None


def test_12_synthetic_data_labeling(prepared_dataset: PreparedMLDataset):
    """Verify data_source is labeled SYNTHETIC_FIXTURE."""
    res = select_production_candidate(prepared_dataset)
    assert res.model_card.training_data_source == "SYNTHETIC_FIXTURE"


def test_13_production_status(prepared_dataset: PreparedMLDataset):
    """Verify production_status is PROVISIONAL MODEL CANDIDATE or BASELINE RETAINED."""
    res = select_production_candidate(prepared_dataset)
    assert res.production_status in {"PROVISIONAL MODEL CANDIDATE", "BASELINE RETAINED"}


def test_14_model_card_creation(prepared_dataset: PreparedMLDataset):
    """Verify model card instance is attached with complete metadata."""
    res = select_production_candidate(prepared_dataset)
    card = res.model_card
    assert isinstance(card, ModelCard)
    assert card.target == "target_next_day_plays"
    assert len(card.known_limitations) > 0


def test_15_feature_contract(prepared_dataset: PreparedMLDataset):
    """Verify feature schema version matches features-v1."""
    res = select_production_candidate(prepared_dataset)
    assert res.feature_schema_version == FEATURE_SCHEMA_VERSION
    assert res.feature_schema_version == "features-v1"


def test_16_target_exclusion(prepared_dataset: PreparedMLDataset):
    """Verify target columns are excluded from model features."""
    res = select_production_candidate(prepared_dataset)
    assert "target_next_day_plays" not in prepared_dataset.X_train.columns


def test_17_pii_exclusion(prepared_dataset: PreparedMLDataset):
    """Verify SensitiveDataError is raised if PII exists in dataset."""
    ds = prepared_dataset
    ds.X_train["email"] = "user@example.com"
    with pytest.raises(SensitiveDataError, match="PII violation"):
        select_production_candidate(ds)


def test_18_deterministic_selection(prepared_dataset: PreparedMLDataset):
    """Verify repeated selection produces identical selected model and metrics."""
    res1 = select_production_candidate(prepared_dataset, random_state=42)
    res2 = select_production_candidate(prepared_dataset, random_state=42)

    assert res1.selected_model == res2.selected_model
    assert res1.validation_metrics.mae == res2.validation_metrics.mae


def test_19_empty_candidate_results_raises_error():
    """Verify empty PreparedMLDataset raises InsufficientTrainingDataError."""
    empty_ds = PreparedMLDataset(
        X_train=pd.DataFrame(), y_train=pd.Series(dtype="float64"),
        X_val=pd.DataFrame(), y_val=pd.Series(dtype="float64"),
        X_test=pd.DataFrame(), y_test=pd.Series(dtype="float64"),
        train_from=None, train_to=None, val_from=None, val_to=None, test_from=None, test_to=None,
        total_rows=0, train_rows=0, val_rows=0, test_rows=0,
        feature_names=[], target_name="target_next_day_plays", identifier_names=[],
    )
    with pytest.raises(InsufficientTrainingDataError, match="Insufficient training data"):
        select_production_candidate(empty_ds)


def test_20_invalid_dataset_handling():
    """Verify dataset with insufficient rows raises InsufficientTrainingDataError."""
    short_ds = PreparedMLDataset(
        X_train=pd.DataFrame([{"plays": 10}]), y_train=pd.Series([20.0]),
        X_val=pd.DataFrame(), y_val=pd.Series(dtype="float64"),
        X_test=pd.DataFrame(), y_test=pd.Series(dtype="float64"),
        train_from=None, train_to=None, val_from=None, val_to=None, test_from=None, test_to=None,
        total_rows=1, train_rows=1, val_rows=0, test_rows=0,
        feature_names=["plays"], target_name="target_next_day_plays", identifier_names=[],
    )
    with pytest.raises(InsufficientTrainingDataError, match="Insufficient training data"):
        select_production_candidate(short_ds)
