"""Tests for Phase 7.6 Checkpoint 10: ML Prediction Service Unit & Preprocessor Isolation Tests."""

from datetime import date, timedelta
from pathlib import Path
from typing import Tuple
from unittest.mock import MagicMock
import numpy as np
import pandas as pd
import pytest

from app.ml.features.feature_builder import build_ml_features
from app.ml.models.model_selector import select_production_candidate
from app.ml.models.model_trainer import ModelTrainer
from app.ml.models.training_dataset import PreparedMLDataset, prepare_training_dataset
from app.ml.registry import ModelRegistry
from app.ml.serving.prediction_service import (
    ModelNotReadyError,
    ModelOutputInvalidError,
    PredictionService,
)
from app.ml.serving.schemas import (
    PredictionInputRecord,
    PredictionRequest,
)
from app.processing.dataframe_builder import build_dataframe
from app.schemas.contract import AnalyticsExportRecord


@pytest.fixture
def multi_day_records() -> list[AnalyticsExportRecord]:
    """Generate 25 days of analytics records."""
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
def registered_model(tmp_path: Path, multi_day_records) -> Tuple[Path, PredictionService]:
    """Train and register model in tmp_path."""
    raw_df = build_dataframe(multi_day_records)
    feature_df = build_ml_features(raw_df, include_targets=False)
    prepared_dataset = prepare_training_dataset(feature_df)

    sel = select_production_candidate(prepared_dataset)
    trainer = ModelTrainer(random_state=42)
    train_res = trainer.train_and_evaluate(prepared_dataset)

    reg = ModelRegistry(artifact_dir=tmp_path)
    reg.save_model_selection_result(
        selection_result=sel,
        prepared_dataset=prepared_dataset,
        model_instance=train_res.model_instance,
        pipeline_instance=train_res.pipeline_instance,
        model_version="plays-predictor-v1",
    )
    reg.activate_model("plays_predictor", "plays-predictor-v1")

    service = PredictionService(artifact_dir=tmp_path)
    service.initialize()
    return tmp_path, service


def test_1_successful_single_prediction(registered_model):
    """Verify single input record prediction returns valid prediction response."""
    _, service = registered_model
    req = PredictionRequest(
        records=[
            PredictionInputRecord(
                content_id=101,
                date=date(2026, 8, 20),
                platform="IOS",
                plays=150.0,
                sessions=160.0,
            )
        ]
    )
    res = service.predict(req, request_id="test-req-1")
    assert res.success is True
    assert res.prediction_count == 1
    assert res.predictions[0].predicted_next_day_plays >= 0.0


def test_2_successful_batch_prediction(registered_model):
    """Verify batch prediction returns prediction items for all input records."""
    _, service = registered_model
    records = [
        PredictionInputRecord(content_id=i, date=date(2026, 8, 20), platform="IOS", plays=float(i * 10))
        for i in range(1, 6)
    ]
    req = PredictionRequest(records=records)
    res = service.predict(req, request_id="test-req-batch")
    assert res.prediction_count == 5
    assert len(res.predictions) == 5


def test_3_preprocessor_never_fit_during_predict(registered_model):
    """Regression test proving preprocessor fit() and fit_transform() are never called."""
    _, service = registered_model
    req = PredictionRequest(
        records=[PredictionInputRecord(content_id=101, date=date(2026, 8, 20), platform="IOS", plays=100.0)]
    )

    bundle, _ = service._load_active_model()
    mock_prep = MagicMock()
    n_feats = getattr(bundle.model, "n_features_in_", len(bundle.feature_names))
    mock_prep.transform.return_value = np.zeros((1, n_feats))
    service._cached_bundle.preprocessor = mock_prep

    service.predict(req, request_id="test-fit-guard")

    mock_prep.transform.assert_called_once()
    mock_prep.fit.assert_not_called()
    mock_prep.fit_transform.assert_not_called()


def test_4_deterministic_prediction(registered_model):
    """Verify predictions are identical across multiple identical invocations."""
    _, service = registered_model
    req = PredictionRequest(
        records=[PredictionInputRecord(content_id=101, date=date(2026, 8, 20), platform="IOS", plays=150.0)]
    )

    res1 = service.predict(req, request_id="req-1")
    res2 = service.predict(req, request_id="req-2")

    assert res1.predictions[0].predicted_next_day_plays == res2.predictions[0].predicted_next_day_plays


def test_5_no_active_model_raises_ready_error(tmp_path: Path):
    """Verify PredictionService raises ModelNotReadyError when no active model is in registry."""
    service = PredictionService(artifact_dir=tmp_path)
    service.initialize()

    req = PredictionRequest(
        records=[PredictionInputRecord(content_id=101, date=date(2026, 8, 20), platform="IOS", plays=100.0)]
    )
    with pytest.raises(ModelNotReadyError):
        service.predict(req, request_id="test-no-model")


def test_6_negative_prediction_clipping(registered_model):
    """Verify negative model raw output values are clipped to non-negative floats."""
    _, service = registered_model
    bundle, _ = service._load_active_model()
    mock_model = MagicMock()
    mock_model.predict.return_value = np.array([-50.0, 120.0])
    service._cached_bundle.model = mock_model

    req = PredictionRequest(
        records=[
            PredictionInputRecord(content_id=1, date=date(2026, 8, 20), platform="IOS"),
            PredictionInputRecord(content_id=2, date=date(2026, 8, 20), platform="IOS"),
        ]
    )
    res = service.predict(req, request_id="test-clip")
    assert res.predictions[0].predicted_next_day_plays == 0.0
    assert res.predictions[1].predicted_next_day_plays == 120.0


def test_7_nan_prediction_output_rejection(registered_model):
    """Verify ModelOutputInvalidError is raised if estimator produces NaN."""
    _, service = registered_model
    mock_model = MagicMock()
    mock_model.predict.return_value = np.array([np.nan])
    service._cached_bundle.model = mock_model

    req = PredictionRequest(
        records=[PredictionInputRecord(content_id=1, date=date(2026, 8, 20), platform="IOS")]
    )
    with pytest.raises(ModelOutputInvalidError, match="invalid non-finite output"):
        service.predict(req, request_id="test-nan")


def test_8_infinity_prediction_output_rejection(registered_model):
    """Verify ModelOutputInvalidError is raised if estimator produces Infinity."""
    _, service = registered_model
    mock_model = MagicMock()
    mock_model.predict.return_value = np.array([np.inf])
    service._cached_bundle.model = mock_model

    req = PredictionRequest(
        records=[PredictionInputRecord(content_id=1, date=date(2026, 8, 20), platform="IOS")]
    )
    with pytest.raises(ModelOutputInvalidError, match="invalid non-finite output"):
        service.predict(req, request_id="test-inf")


def test_9_target_column_rejection():
    """Verify target column in input payload triggers validation error."""
    with pytest.raises(ValueError, match="TARGET_COLUMN_PRESENT"):
        PredictionInputRecord.model_validate(
            {"content_id": 1, "date": "2026-08-20", "platform": "IOS", "target_next_day_plays": 100.0}
        )


def test_10_pii_field_rejection():
    """Verify PII field in input payload triggers validation error."""
    with pytest.raises(ValueError, match="PII_SUPPLIED"):
        PredictionInputRecord.model_validate(
            {"content_id": 1, "date": "2026-08-20", "platform": "IOS", "email": "user@example.com"}
        )


def test_11_negative_metric_rejection():
    """Verify negative plays value triggers validation error."""
    with pytest.raises(ValueError, match="non-negative"):
        PredictionInputRecord(content_id=1, date=date(2026, 8, 20), platform="IOS", plays=-10.0)


def test_12_invalid_completion_rate_rejection():
    """Verify completion_rate > 1.0 triggers validation error."""
    with pytest.raises(ValueError, match="between 0.0 and 1.0"):
        PredictionInputRecord(content_id=1, date=date(2026, 8, 20), platform="IOS", completion_rate=1.5)


def test_13_batch_size_exceeded_rejection():
    """Verify > 100 records in PredictionRequest triggers validation error."""
    records = [
        PredictionInputRecord(content_id=i, date=date(2026, 8, 20), platform="IOS") for i in range(101)
    ]
    with pytest.raises(ValueError, match="exceeds maximum allowable limit of 100 records"):
        PredictionRequest(records=records)


def test_14_exactly_100_records_allowed():
    """Verify exactly 100 records in PredictionRequest is accepted."""
    records = [
        PredictionInputRecord(content_id=i, date=date(2026, 8, 20), platform="IOS") for i in range(100)
    ]
    req = PredictionRequest(records=records)
    assert len(req.records) == 100


def test_15_empty_batch_rejection():
    """Verify empty record list triggers validation error."""
    with pytest.raises(ValueError, match="at least 1 record"):
        PredictionRequest(records=[])


def test_16_baseline_mode_prediction(tmp_path: Path, multi_day_records):
    """Verify ACTIVE_BASELINE status returns plays = current plays."""
    raw_df = build_dataframe(multi_day_records)
    feature_df = build_ml_features(raw_df, include_targets=False)
    prepared_dataset = prepare_training_dataset(feature_df)

    sel = select_production_candidate(prepared_dataset)
    sel.production_status = "BASELINE_RETAINED"
    sel.selected_model = "naive_previous_day_plays"

    reg = ModelRegistry(artifact_dir=tmp_path)
    reg.save_model_selection_result(selection_result=sel, prepared_dataset=prepared_dataset, model_version="plays-base-v1")

    service = PredictionService(artifact_dir=tmp_path)
    service.initialize()

    req = PredictionRequest(
        records=[PredictionInputRecord(content_id=1, date=date(2026, 8, 20), platform="IOS", plays=245.0)]
    )
    res = service.predict(req, request_id="base-test")
    assert res.predictions[0].predicted_next_day_plays == 245.0


def test_17_readiness_probe_ready(registered_model):
    """Verify get_readiness returns READY for active valid model."""
    _, service = registered_model
    ready = service.get_readiness()
    assert ready.status == "READY"


def test_18_model_status_response(registered_model):
    """Verify get_model_status returns active version and algorithm metadata."""
    _, service = registered_model
    stat = service.get_model_status()
    assert stat.model_name == "plays_predictor"
    assert stat.status in ["PROVISIONAL", "ACTIVE"]


def test_19_nan_feature_input_rejection():
    """Verify NaN feature input value raises validation error."""
    with pytest.raises(ValueError, match="finite"):
        PredictionInputRecord(content_id=1, date=date(2026, 8, 20), platform="IOS", plays=float("nan"))


def test_20_inf_feature_input_rejection():
    """Verify Infinity feature input value raises validation error."""
    with pytest.raises(ValueError, match="finite"):
        PredictionInputRecord(content_id=1, date=date(2026, 8, 20), platform="IOS", plays=float("inf"))


def test_21_no_database_network_imports():
    """Verify prediction_service does not import database, Redis, or MinIO packages."""
    import sys
    import app.ml.serving.prediction_service as ps_mod

    source = ps_mod.__file__
    with open(source, "r", encoding="utf-8") as f:
        code = f.read()

    assert "psycopg2" not in code
    assert "sqlalchemy" not in code
    assert "redis" not in code
    assert "minio" not in code


def test_22_request_id_in_response(registered_model):
    """Verify request_id is propagated into PredictionResponse."""
    _, service = registered_model
    req = PredictionRequest(
        records=[PredictionInputRecord(content_id=1, date=date(2026, 8, 20), platform="IOS")]
    )
    res = service.predict(req, request_id="req-custom-12345")
    assert res.request_id == "req-custom-12345"
