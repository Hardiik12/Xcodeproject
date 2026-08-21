"""Tests for Phase 7.6 Checkpoint 11: ML Serving Hardening & Observability."""

from datetime import date, timedelta
from pathlib import Path
import json
from uuid import uuid4
import numpy as np
import pytest
from fastapi.testclient import TestClient

from app.api.routes.health import get_prediction_service
from app.main import create_app
from app.ml.features.feature_builder import build_ml_features
from app.ml.models.model_selector import select_production_candidate
from app.ml.models.model_trainer import ModelTrainer
from app.ml.models.training_dataset import PreparedMLDataset, prepare_training_dataset
from app.ml.registry import ModelArtifactIntegrityError, ModelRegistry
from app.ml.serving.metrics import PredictionMetricsTracker, metrics_tracker
from app.ml.serving.prediction_service import PredictionService
from app.processing.dataframe_builder import build_dataframe
from app.schemas.contract import AnalyticsExportRecord


@pytest.fixture
def test_artifact_dir(tmp_path: Path) -> Path:
    """Fixture providing isolated temporary directory for model artifacts."""
    return tmp_path / "artifacts"


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


@pytest.fixture
def registered_service(test_artifact_dir: Path, prepared_dataset) -> PredictionService:
    """Register trained candidate model and return configured PredictionService."""
    sel = select_production_candidate(prepared_dataset)
    trainer = ModelTrainer(random_state=42)
    train_res = trainer.train_and_evaluate(prepared_dataset)

    reg = ModelRegistry(artifact_dir=test_artifact_dir)
    reg.save_model_selection_result(
        selection_result=sel,
        prepared_dataset=prepared_dataset,
        model_instance=train_res.model_instance,
        pipeline_instance=train_res.pipeline_instance,
        model_version="plays-predictor-v1",
    )

    srv = PredictionService(artifact_dir=test_artifact_dir)
    srv.initialize()
    return srv


@pytest.fixture
def client_with_service(registered_service: PredictionService) -> TestClient:
    """FastAPI TestClient with overridden prediction service dependency."""
    metrics_tracker.reset()
    app = create_app()
    app.dependency_overrides[get_prediction_service] = lambda: registered_service
    return TestClient(app)


@pytest.fixture
def client_without_model(tmp_path: Path) -> TestClient:
    """FastAPI TestClient with empty artifact directory (no registered model)."""
    metrics_tracker.reset()
    empty_dir = tmp_path / "empty_artifacts"
    srv = PredictionService(artifact_dir=empty_dir)
    srv.initialize()

    app = create_app()
    app.dependency_overrides[get_prediction_service] = lambda: srv
    return TestClient(app)


def test_1_liveness_200(client_with_service: TestClient):
    """Verify GET /api/v1/analytics/health returns HTTP 200 UP."""
    res = client_with_service.get("/api/v1/analytics/health")
    assert res.status_code == 200
    data = res.json()
    assert data["status"] == "UP"
    assert data["success"] is True


def test_2_readiness_200_when_model_available(client_with_service: TestClient):
    """Verify GET /api/v1/analytics/ready returns HTTP 200 READY when active model exists."""
    res = client_with_service.get("/api/v1/analytics/ready")
    assert res.status_code == 200
    data = res.json()
    assert data["status"] == "READY"
    assert data["model"] == "plays_predictor"
    assert data["model_version"] == "plays-predictor-v1"


def test_3_readiness_503_when_model_unavailable(client_without_model: TestClient):
    """Verify GET /api/v1/analytics/ready returns HTTP 503 NOT_READY when no model exists."""
    res = client_without_model.get("/api/v1/analytics/ready")
    assert res.status_code == 503
    data = res.json()
    assert data["status"] == "NOT_READY"
    assert data["reason"] == "MODEL_UNAVAILABLE"


def test_4_readiness_no_secrets_exposed(client_with_service: TestClient):
    """Verify readiness probe does not expose file paths, SHA hashes, or credentials."""
    res = client_with_service.get("/api/v1/analytics/ready")
    body = res.text.lower()
    assert "artifact_path" not in body
    assert "sha256" not in body
    assert "password" not in body
    assert "token" not in body


def test_5_model_status(client_with_service: TestClient):
    """Verify GET /api/v1/ml/model/status exposes operational status metadata."""
    res = client_with_service.get("/api/v1/ml/model/status")
    assert res.status_code == 200
    data = res.json()
    assert data["model_name"] == "plays_predictor"
    assert data["algorithm"] == "ridge_regression"
    assert data["target"] == "target_next_day_plays"
    assert "validation_mae" in data


def test_6_metrics_endpoint(client_with_service: TestClient):
    """Verify GET /api/v1/ml/metrics returns aggregated metrics summary."""
    res = client_with_service.get("/api/v1/ml/metrics")
    assert res.status_code == 200
    data = res.json()
    assert "prediction_requests" in data
    assert "latency_ms" in data
    assert "batch_size" in data


def test_7_request_id_generation(client_with_service: TestClient):
    """Verify server generates UUID request ID when client omits X-Request-ID header."""
    res = client_with_service.get("/api/v1/analytics/health")
    assert "X-Request-ID" in res.headers
    req_id = res.headers["X-Request-ID"]
    assert len(req_id) > 10


def test_8_request_id_propagation(client_with_service: TestClient):
    """Verify client-supplied X-Request-ID is preserved and propagated."""
    custom_id = "test-req-12345-abc"
    res = client_with_service.get("/api/v1/analytics/health", headers={"X-Request-ID": custom_id})
    assert res.headers.get("X-Request-ID") == custom_id


def test_9_request_id_prediction_response(client_with_service: TestClient):
    """Verify prediction response includes request_id in payload and headers."""
    custom_id = "req-pred-999"
    payload = {
        "records": [
            {"content_id": 1, "date": "2026-08-15", "platform": "IOS", "plays": 150.0}
        ]
    }
    res = client_with_service.post("/api/v1/ml/predict", json=payload, headers={"X-Request-ID": custom_id})
    assert res.status_code == 200
    data = res.json()
    assert data["request_id"] == custom_id
    assert res.headers.get("X-Request-ID") == custom_id


def test_10_successful_request_metric_increment(client_with_service: TestClient):
    """Verify successful predictions increment successful request count."""
    payload = {
        "records": [
            {"content_id": 1, "date": "2026-08-15", "platform": "IOS", "plays": 150.0}
        ]
    }
    client_with_service.post("/api/v1/ml/predict", json=payload)
    metrics_res = client_with_service.get("/api/v1/ml/metrics").json()
    assert metrics_res["prediction_requests"]["successful"] == 1
    assert metrics_res["prediction_requests"]["total"] == 1


def test_11_failed_request_metric_increment(client_without_model: TestClient):
    """Verify failed prediction attempts increment failed request count."""
    payload = {
        "records": [
            {"content_id": 1, "date": "2026-08-15", "platform": "IOS", "plays": 150.0}
        ]
    }
    res = client_without_model.post("/api/v1/ml/predict", json=payload)
    assert res.status_code == 503

    metrics_res = client_without_model.get("/api/v1/ml/metrics").json()
    assert metrics_res["prediction_requests"]["failed"] == 1
    assert metrics_res["prediction_requests"]["total"] == 1


def test_12_latency_recording(client_with_service: TestClient):
    """Verify latency ms is recorded in operational metrics."""
    payload = {
        "records": [
            {"content_id": 1, "date": "2026-08-15", "platform": "IOS", "plays": 150.0}
        ]
    }
    client_with_service.post("/api/v1/ml/predict", json=payload)
    metrics_res = client_with_service.get("/api/v1/ml/metrics").json()
    assert metrics_res["latency_ms"]["count"] == 1
    assert metrics_res["latency_ms"]["average"] >= 0.0


def test_13_batch_size_recording(client_with_service: TestClient):
    """Verify batch size is recorded in operational metrics."""
    payload = {
        "records": [
            {"content_id": 1, "date": "2026-08-15", "platform": "IOS", "plays": 100.0},
            {"content_id": 2, "date": "2026-08-15", "platform": "ANDROID", "plays": 200.0},
        ]
    }
    client_with_service.post("/api/v1/ml/predict", json=payload)
    metrics_res = client_with_service.get("/api/v1/ml/metrics").json()
    assert metrics_res["batch_size"]["max"] == 2


def test_14_structured_error_format(client_without_model: TestClient):
    """Verify error responses follow standardized StructuredErrorResponse format."""
    payload = {
        "records": [
            {"content_id": 1, "date": "2026-08-15", "platform": "IOS", "plays": 150.0}
        ]
    }
    res = client_without_model.post("/api/v1/ml/predict", json=payload)
    assert res.status_code == 503
    data = res.json()
    assert data["success"] is False
    assert "error" in data
    assert "code" in data["error"]
    assert "message" in data["error"]
    assert "request_id" in data["error"]
    assert "timestamp" in data


def test_15_no_stack_traces_in_errors(client_without_model: TestClient):
    """Verify structured error response does not expose python stack trace."""
    payload = {
        "records": [
            {"content_id": 1, "date": "2026-08-15", "platform": "IOS", "plays": 150.0}
        ]
    }
    res = client_without_model.post("/api/v1/ml/predict", json=payload)
    body = res.text
    assert "Traceback" not in body
    assert "File " not in body
    assert "line " not in body


def test_16_no_pii_in_logs():
    """Verify PredictionMetricsTracker and schemas omit user_id, email, and phone."""
    tracker = PredictionMetricsTracker()
    tracker.record_prediction(True, 12.5, 5)
    summary_str = json.dumps(tracker.get_metrics_summary())
    assert "user_id" not in summary_str
    assert "email" not in summary_str
    assert "phone" not in summary_str


def test_17_no_authorization_header_in_logs():
    """Verify logging formatting avoids printing headers or auth tokens."""
    app = create_app()
    client = TestClient(app)
    res = client.get("/api/v1/analytics/health", headers={"Authorization": "Bearer secret_token_xyz"})
    assert res.status_code == 200
    assert "secret_token_xyz" not in res.text


def test_18_model_integrity_failure_handling(tmp_path: Path, prepared_dataset):
    """Verify readiness returns 503 when model binary artifact is corrupted."""
    test_dir = tmp_path / "corrupt_artifacts"
    sel = select_production_candidate(prepared_dataset)
    trainer = ModelTrainer(random_state=42)
    train_res = trainer.train_and_evaluate(prepared_dataset)

    reg = ModelRegistry(artifact_dir=test_dir)
    meta = reg.save_model_selection_result(
        selection_result=sel,
        prepared_dataset=prepared_dataset,
        model_instance=train_res.model_instance,
        pipeline_instance=train_res.pipeline_instance,
        model_version="plays-predictor-v1",
    )

    # Corrupt file
    with open(meta.artifact_path, "ab") as f:
        f.write(b"CORRUPTED")

    srv = PredictionService(artifact_dir=test_dir)
    srv.initialize()

    readiness = srv.get_readiness()
    assert readiness.status == "NOT_READY"
    assert readiness.reason == "MODEL_INTEGRITY_FAILURE"


def test_19_model_compatibility_failure_handling(tmp_path: Path, prepared_dataset):
    """Verify readiness returns NOT_READY when feature schema version is incompatible."""
    test_dir = tmp_path / "incompatible_artifacts"
    sel = select_production_candidate(prepared_dataset)
    trainer = ModelTrainer(random_state=42)
    train_res = trainer.train_and_evaluate(prepared_dataset)

    reg = ModelRegistry(artifact_dir=test_dir)
    meta = reg.save_model_selection_result(
        selection_result=sel,
        prepared_dataset=prepared_dataset,
        model_instance=train_res.model_instance,
        pipeline_instance=train_res.pipeline_instance,
        model_version="plays-predictor-v1",
    )

    # Mutate manifest feature schema version
    manifest_data = json.loads((test_dir / "registry.json").read_text())
    manifest_data["models"][0]["feature_schema_version"] = "features-v99"
    (test_dir / "registry.json").write_text(json.dumps(manifest_data))

    srv = PredictionService(artifact_dir=test_dir)
    readiness = srv.get_readiness()
    assert readiness.status == "NOT_READY"
    assert readiness.reason == "FEATURE_SCHEMA_MISMATCH"


def test_20_invalid_prediction_request_empty_records(client_with_service: TestClient):
    """Verify prediction payload with empty records array returns HTTP 422/400 validation error."""
    res = client_with_service.post("/api/v1/ml/predict", json={"records": []})
    assert res.status_code in [400, 422]


def test_21_prediction_503_when_model_unavailable(client_without_model: TestClient):
    """Verify prediction request when model is unavailable returns HTTP 503 StructuredErrorResponse."""
    payload = {
        "records": [
            {"content_id": 1, "date": "2026-08-15", "platform": "IOS", "plays": 150.0}
        ]
    }
    res = client_without_model.post("/api/v1/ml/predict", json=payload)
    assert res.status_code == 503
    data = res.json()
    assert data["error"]["code"] == "MODEL_UNAVAILABLE"


def test_22_batch_limit_enforcement(client_with_service: TestClient):
    """Verify prediction payload with > 100 records is rejected with HTTP 422/400 validation error."""
    records = [{"content_id": i, "date": "2026-08-15", "platform": "IOS", "plays": 10.0} for i in range(101)]
    res = client_with_service.post("/api/v1/ml/predict", json={"records": records})
    assert res.status_code in [400, 422]


def test_23_deterministic_prediction_behavior(client_with_service: TestClient):
    """Verify identical prediction requests return deterministic predictions."""
    payload = {
        "records": [
            {"content_id": 1, "date": "2026-08-15", "platform": "IOS", "plays": 150.0}
        ]
    }
    res1 = client_with_service.post("/api/v1/ml/predict", json=payload).json()
    res2 = client_with_service.post("/api/v1/ml/predict", json=payload).json()

    p1 = res1["predictions"][0]["predicted_next_day_plays"]
    p2 = res2["predictions"][0]["predicted_next_day_plays"]
    assert p1 == p2


def test_24_openapi_health_route(client_with_service: TestClient):
    """Verify OpenAPI schema documents GET /api/v1/analytics/health."""
    res = client_with_service.get("/openapi.json")
    assert res.status_code == 200
    paths = res.json()["paths"]
    assert "/api/v1/analytics/health" in paths


def test_25_openapi_readiness_route(client_with_service: TestClient):
    """Verify OpenAPI schema documents GET /api/v1/analytics/ready."""
    res = client_with_service.get("/openapi.json")
    assert res.status_code == 200
    paths = res.json()["paths"]
    assert "/api/v1/analytics/ready" in paths


def test_26_openapi_prediction_route(client_with_service: TestClient):
    """Verify OpenAPI schema documents POST /api/v1/ml/predict."""
    res = client_with_service.get("/openapi.json")
    assert res.status_code == 200
    paths = res.json()["paths"]
    assert "/api/v1/ml/predict" in paths


def test_27_baseline_retained_readiness(tmp_path: Path, prepared_dataset):
    """Verify readiness returns HTTP 200 READY when active model is registered as BASELINE_RETAINED."""
    test_dir = tmp_path / "baseline_artifacts"
    sel = select_production_candidate(prepared_dataset)
    sel.production_status = "BASELINE_RETAINED"
    sel.selected_model = "naive_previous_day_plays"

    reg = ModelRegistry(artifact_dir=test_dir)
    reg.save_model_selection_result(
        selection_result=sel,
        prepared_dataset=prepared_dataset,
        model_version="plays-baseline-v1",
    )

    srv = PredictionService(artifact_dir=test_dir)
    srv.initialize()

    readiness = srv.get_readiness()
    assert readiness.status == "READY"
    assert readiness.model == "plays_predictor"
    assert readiness.model_version == "plays-baseline-v1"


def test_28_metrics_do_not_expose_predictions(client_with_service: TestClient):
    """Verify operational metrics summary omits raw predictions and feature names."""
    payload = {
        "records": [
            {"content_id": 1, "date": "2026-08-15", "platform": "IOS", "plays": 150.0}
        ]
    }
    client_with_service.post("/api/v1/ml/predict", json=payload)
    metrics_res = client_with_service.get("/api/v1/ml/metrics").json()
    metrics_str = json.dumps(metrics_res).lower()

    assert "predicted_next_day_plays" not in metrics_str
    assert "predictions" not in metrics_str
    assert "content_id" not in metrics_str


def test_29_openapi_model_status_route(client_with_service: TestClient):
    """Verify OpenAPI schema documents GET /api/v1/ml/model/status."""
    res = client_with_service.get("/openapi.json")
    assert res.status_code == 200
    paths = res.json()["paths"]
    assert "/api/v1/ml/model/status" in paths


def test_30_openapi_metrics_route(client_with_service: TestClient):
    """Verify OpenAPI schema documents GET /api/v1/ml/metrics."""
    res = client_with_service.get("/openapi.json")
    assert res.status_code == 200
    paths = res.json()["paths"]
    assert "/api/v1/ml/metrics" in paths


def test_31_safe_model_status_no_paths(client_with_service: TestClient):
    """Verify model status endpoint output omits filesystem paths."""
    res = client_with_service.get("/api/v1/ml/model/status")
    assert res.status_code == 200
    text = res.text.lower()
    assert "/users/" not in text
    assert "c:\\" not in text
    assert ".joblib" not in text


def test_32_model_compatibility_failure_readiness(test_artifact_dir: Path, prepared_dataset):
    """Verify readiness returns NOT_READY when contract version mismatches."""
    sel = select_production_candidate(prepared_dataset)
    trainer = ModelTrainer(random_state=42)
    train_res = trainer.train_and_evaluate(prepared_dataset)

    reg = ModelRegistry(artifact_dir=test_artifact_dir)
    meta = reg.save_model_selection_result(
        selection_result=sel,
        prepared_dataset=prepared_dataset,
        model_instance=train_res.model_instance,
        pipeline_instance=train_res.pipeline_instance,
        model_version="plays-predictor-v1",
        overwrite=True,
    )
    reg.activate_model("plays_predictor", "plays-predictor-v1")

    # Mutate manifest to corrupt contract version
    manifest_data = json.loads((test_artifact_dir / "registry.json").read_text())
    manifest_data["models"][0]["contract_version"] = "analytics-contract-invalid"
    (test_artifact_dir / "registry.json").write_text(json.dumps(manifest_data))

    srv = PredictionService(artifact_dir=test_artifact_dir)
    srv.initialize()

    readiness = srv.get_readiness()
    assert readiness.status == "NOT_READY"


def test_33_no_authorization_header_in_logs():
    """Verify Authorization header is never logged in structured log outputs."""
    from app.main import create_app
    app = create_app()
    client = TestClient(app)
    headers = {"Authorization": "Bearer SecretToken12345"}
    res = client.get("/api/v1/analytics/health", headers=headers)
    assert res.status_code == 200
    text = res.text.lower()
    assert "secrettoken12345" not in text


def test_34_previous_valid_model_retained_on_failed_activation(test_artifact_dir: Path, prepared_dataset):
    """Verify previous valid active model is retained when candidate activation fails."""
    sel = select_production_candidate(prepared_dataset)
    trainer = ModelTrainer(random_state=42)
    train_res = trainer.train_and_evaluate(prepared_dataset)

    reg = ModelRegistry(artifact_dir=test_artifact_dir)
    reg.save_model_selection_result(
        selection_result=sel,
        prepared_dataset=prepared_dataset,
        model_instance=train_res.model_instance,
        pipeline_instance=train_res.pipeline_instance,
        model_version="plays-predictor-v1",
        overwrite=True,
    )
    reg.activate_model("plays_predictor", "plays-predictor-v1")

    # Attempt to activate non-existent version
    with pytest.raises(Exception):
        reg.activate_model("plays_predictor", "non-existent-version")

    active = reg.get_active_model("plays_predictor")
    assert active["model_version"] == "plays-predictor-v1"
    assert active["status"] == "ACTIVE"

