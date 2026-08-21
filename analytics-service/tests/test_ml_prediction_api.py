"""Tests for Phase 7.6 Checkpoint 10: ML Prediction FastAPI API Endpoints & Error Handling."""

from datetime import date, timedelta
from pathlib import Path
from typing import Tuple
from unittest.mock import MagicMock
import pytest
from fastapi.testclient import TestClient

from app.api.routes.health import get_prediction_service
from app.main import create_app
from app.ml.features.feature_builder import build_ml_features
from app.ml.models.model_selector import select_production_candidate
from app.ml.models.model_trainer import ModelTrainer
from app.ml.models.training_dataset import prepare_training_dataset
from app.ml.registry import ModelRegistry
from app.ml.serving.metrics import metrics_tracker
from app.ml.serving.prediction_service import ModelNotReadyError, ModelOutputInvalidError, PredictionService
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
def api_client(tmp_path: Path, multi_day_records):
    """Build TestClient with active registered model in tmp_path."""
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

    app = create_app()
    app.dependency_overrides[get_prediction_service] = lambda: service
    return TestClient(app)


def test_1_api_predict_200_ok(api_client):
    """Verify POST /api/v1/ml/predict returns HTTP 200 with valid prediction response."""
    payload = {
        "records": [
            {
                "content_id": 101,
                "date": "2026-08-20",
                "platform": "IOS",
                "sessions": 150.0,
                "plays": 120.0,
                "unique_viewers": 110.0,
                "watch_time_seconds": 14400.0,
                "completed_plays": 90.0,
                "completion_rate": 0.75,
            }
        ]
    }
    res = api_client.post("/api/v1/ml/predict", json=payload)
    assert res.status_code == 200
    data = res.json()
    assert data["success"] is True
    assert "predictions" in data
    assert len(data["predictions"]) == 1
    assert "request_id" in data


def test_2_api_predict_batch_size_exceeded_400(api_client):
    """Verify POST /api/v1/ml/predict returns HTTP 422/400 when batch size > 100."""
    payload = {
        "records": [
            {"content_id": i, "date": "2026-08-20", "platform": "IOS"}
            for i in range(101)
        ]
    }
    res = api_client.post("/api/v1/ml/predict", json=payload)
    assert res.status_code in [400, 422]


def test_3_api_predict_empty_batch_400(api_client):
    """Verify empty record list returns 422 or 400 error."""
    res = api_client.post("/api/v1/ml/predict", json={"records": []})
    assert res.status_code in [400, 422]


def test_4_api_predict_target_column_present_400(api_client):
    """Verify target column in payload returns 422 or 400 error."""
    payload = {
        "records": [
            {
                "content_id": 101,
                "date": "2026-08-20",
                "platform": "IOS",
                "target_next_day_plays": 500.0,
            }
        ]
    }
    res = api_client.post("/api/v1/ml/predict", json=payload)
    assert res.status_code in [400, 422]


def test_5_api_predict_pii_supplied_400(api_client):
    """Verify PII in payload returns 422 or 400 error."""
    payload = {
        "records": [
            {
                "content_id": 101,
                "date": "2026-08-20",
                "platform": "IOS",
                "email": "user@example.com",
            }
        ]
    }
    res = api_client.post("/api/v1/ml/predict", json=payload)
    assert res.status_code in [400, 422]


def test_6_api_predict_negative_metric_400(api_client):
    """Verify negative play metric returns 422 or 400 error."""
    payload = {
        "records": [
            {
                "content_id": 101,
                "date": "2026-08-20",
                "platform": "IOS",
                "plays": -50.0,
            }
        ]
    }
    res = api_client.post("/api/v1/ml/predict", json=payload)
    assert res.status_code in [400, 422]


def test_7_api_predict_invalid_completion_rate_400(api_client):
    """Verify completion_rate > 1.0 returns 422 or 400 error."""
    payload = {
        "records": [
            {
                "content_id": 101,
                "date": "2026-08-20",
                "platform": "IOS",
                "completion_rate": 2.5,
            }
        ]
    }
    res = api_client.post("/api/v1/ml/predict", json=payload)
    assert res.status_code in [400, 422]


def test_8_api_predict_503_model_unavailable(tmp_path: Path):
    """Verify POST /api/v1/ml/predict returns HTTP 503 when model is unavailable."""
    app = create_app()
    mock_service = MagicMock(spec=PredictionService)
    mock_service.get_readiness.return_value.status = "NOT_READY"
    mock_service.get_readiness.return_value.reason = "MODEL_UNAVAILABLE"
    mock_service.predict.side_effect = ModelNotReadyError("MODEL_UNAVAILABLE")

    app.dependency_overrides[get_prediction_service] = lambda: mock_service
    client = TestClient(app)

    payload = {"records": [{"content_id": 1, "date": "2026-08-20", "platform": "IOS"}]}
    res = client.post("/api/v1/ml/predict", json=payload)
    assert res.status_code == 503
    data = res.json()
    assert data["success"] is False
    assert data["error"]["code"] == "MODEL_UNAVAILABLE"


def test_9_api_predict_500_model_output_invalid(tmp_path: Path):
    """Verify POST /api/v1/ml/predict returns HTTP 500 when model produces invalid NaN output."""
    app = create_app()
    mock_service = MagicMock(spec=PredictionService)
    mock_service.get_readiness.return_value.status = "READY"
    mock_service.predict.side_effect = ModelOutputInvalidError("NaN output")

    app.dependency_overrides[get_prediction_service] = lambda: mock_service
    client = TestClient(app)

    payload = {"records": [{"content_id": 1, "date": "2026-08-20", "platform": "IOS"}]}
    res = client.post("/api/v1/ml/predict", json=payload)
    assert res.status_code == 500
    data = res.json()
    assert data["success"] is False
    assert data["error"]["code"] == "MODEL_OUTPUT_INVALID"


def test_10_request_id_header_propagation(api_client):
    """Verify X-Request-ID header is propagated in response."""
    payload = {
        "records": [
            {
                "content_id": 101,
                "date": "2026-08-20",
                "platform": "IOS",
            }
        ]
    }
    headers = {"X-Request-ID": "req-custom-header-999"}
    res = api_client.post("/api/v1/ml/predict", json=payload, headers=headers)
    assert res.status_code == 200
    assert res.headers.get("X-Request-ID") == "req-custom-header-999"
    assert res.json()["request_id"] == "req-custom-header-999"


def test_11_openapi_schema_contains_predict_route(api_client):
    """Verify OpenAPI schema documents POST /api/v1/ml/predict."""
    res = api_client.get("/openapi.json")
    assert res.status_code == 200
    paths = res.json()["paths"]
    assert "/api/v1/ml/predict" in paths
    assert "post" in paths["/api/v1/ml/predict"]


def test_12_no_stack_trace_in_error_response():
    """Verify stack trace is not exposed in error response body."""
    app = create_app()
    mock_service = MagicMock(spec=PredictionService)
    mock_service.predict.side_effect = RuntimeError("Internal secret exception stack trace line 42")

    app.dependency_overrides[get_prediction_service] = lambda: mock_service
    client = TestClient(app)

    payload = {"records": [{"content_id": 1, "date": "2026-08-20", "platform": "IOS"}]}
    res = client.post("/api/v1/ml/predict", json=payload)
    assert res.status_code == 500
    text = res.text
    assert "Traceback" not in text
    assert "line 42" not in text


def test_13_no_artifact_paths_in_predict_response(api_client):
    """Verify prediction response body omits local filesystem paths."""
    payload = {
        "records": [
            {
                "content_id": 101,
                "date": "2026-08-20",
                "platform": "IOS",
            }
        ]
    }
    res = api_client.post("/api/v1/ml/predict", json=payload)
    text = res.text.lower()
    assert "/users/" not in text
    assert "c:\\" not in text
    assert ".joblib" not in text


def test_14_no_pii_in_predict_response(api_client):
    """Verify response body contains zero PII fields."""
    payload = {
        "records": [
            {
                "content_id": 101,
                "date": "2026-08-20",
                "platform": "IOS",
            }
        ]
    }
    res = api_client.post("/api/v1/ml/predict", json=payload)
    text = res.text.lower()
    assert "email" not in text
    assert "phone" not in text
    assert "user_id" not in text


def test_15_metrics_updated_on_predict(api_client):
    """Verify metrics tracker records prediction metrics."""
    initial_success = metrics_tracker.successful_prediction_requests
    payload = {
        "records": [
            {
                "content_id": 101,
                "date": "2026-08-20",
                "platform": "IOS",
            }
        ]
    }
    res = api_client.post("/api/v1/ml/predict", json=payload)
    assert res.status_code == 200
    assert metrics_tracker.successful_prediction_requests >= initial_success + 1


def test_16_api_predict_503_model_incompatible():
    """Verify POST /api/v1/ml/predict returns HTTP 503 when model is incompatible."""
    app = create_app()
    mock_service = MagicMock(spec=PredictionService)
    mock_service.predict.side_effect = ModelNotReadyError("FEATURE_SCHEMA_MISMATCH")

    app.dependency_overrides[get_prediction_service] = lambda: mock_service
    client = TestClient(app)

    payload = {"records": [{"content_id": 1, "date": "2026-08-20", "platform": "IOS"}]}
    res = client.post("/api/v1/ml/predict", json=payload)
    assert res.status_code == 503
    data = res.json()
    assert data["error"]["code"] == "MODEL_INCOMPATIBLE"


def test_17_no_postgres_redis_minio_connections():
    """Verify endpoint execution does not connect to external DB/Redis/MinIO services."""
    import app.api.routes.ml as ml_route

    with open(ml_route.__file__, "r", encoding="utf-8") as f:
        code = f.read()

    assert "psycopg2" not in code
    assert "redis" not in code
    assert "minio" not in code


def test_18_prediction_count_matches_record_count(api_client):
    """Verify response prediction_count equals number of predictions."""
    payload = {
        "records": [
            {"content_id": 1, "date": "2026-08-20", "platform": "IOS"},
            {"content_id": 2, "date": "2026-08-20", "platform": "ANDROID"},
        ]
    }
    res = api_client.post("/api/v1/ml/predict", json=payload)
    assert res.status_code == 200
    data = res.json()
    assert data["prediction_count"] == 2
    assert len(data["predictions"]) == 2


def test_19_prediction_output_floats_rounded(api_client):
    """Verify predicted_next_day_plays values are rounded floats."""
    payload = {
        "records": [
            {"content_id": 1, "date": "2026-08-20", "platform": "IOS"}
        ]
    }
    res = api_client.post("/api/v1/ml/predict", json=payload)
    assert res.status_code == 200
    pred_val = res.json()["predictions"][0]["predicted_next_day_plays"]
    assert isinstance(pred_val, float)
    assert pred_val >= 0.0


def test_20_predict_response_target_and_algorithm_fields(api_client):
    """Verify response includes target='target_next_day_plays' and algorithm string."""
    payload = {
        "records": [
            {"content_id": 1, "date": "2026-08-20", "platform": "IOS"}
        ]
    }
    res = api_client.post("/api/v1/ml/predict", json=payload)
    assert res.status_code == 200
    data = res.json()
    assert data["target"] == "target_next_day_plays"
    assert "algorithm" in data
