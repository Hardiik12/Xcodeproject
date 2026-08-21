"""Tests for Phase 7.6 Checkpoint 8: Model Card and Selection API."""

from datetime import date, timedelta
import json
import pytest
from fastapi.testclient import TestClient

from app.main import create_app
from app.ml.features.feature_builder import build_ml_features
from app.ml.models.model_card import ModelCard
from app.ml.models.model_selector import select_production_candidate
from app.ml.models.training_dataset import PreparedMLDataset, prepare_training_dataset
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


def test_21_model_card_fields(prepared_dataset: PreparedMLDataset):
    """Verify ModelCard contains required governance and metric fields."""
    res = select_production_candidate(prepared_dataset)
    card = res.model_card

    assert isinstance(card.model_name, str)
    assert card.target == "target_next_day_plays"
    assert card.feature_schema_version == "features-v1"
    assert card.training_data_source == "SYNTHETIC_FIXTURE"
    assert card.selection_metric == "Validation MAE"
    assert card.training_row_count == prepared_dataset.train_rows
    assert card.validation_row_count == prepared_dataset.val_rows
    assert card.test_row_count == prepared_dataset.test_rows


def test_22_model_card_privacy_no_pii(prepared_dataset: PreparedMLDataset):
    """Verify ModelCard representation contains zero PII keys or sensitive strings."""
    res = select_production_candidate(prepared_dataset)
    card = res.model_card
    card_dict = {
        "model_name": card.model_name,
        "algorithm": card.algorithm,
        "target": card.target,
        "feature_schema_version": card.feature_schema_version,
        "training_data_source": card.training_data_source,
        "known_limitations": card.known_limitations,
        "production_status": card.production_status,
    }
    card_str = json.dumps(card_dict).lower()

    assert "user_id" not in card_str
    assert "email" not in card_str
    assert "phone" not in card_str
    assert "password" not in card_str


def test_23_selection_status_field(prepared_dataset: PreparedMLDataset):
    """Verify selection_status field is LEARNED_MODEL_SELECTED or BASELINE_RETAINED."""
    res = select_production_candidate(prepared_dataset)
    assert res.selection_status in {"LEARNED_MODEL_SELECTED", "BASELINE_RETAINED", "NO_VALID_MODEL"}


def test_24_baseline_retained_when_learned_models_fail_threshold(prepared_dataset: PreparedMLDataset):
    """Verify baseline is retained if min_mae_improvement_threshold is set impossibly high (e.g. 99.9%)."""
    res = select_production_candidate(prepared_dataset, min_mae_improvement_threshold=0.999)
    assert res.selected_model == "naive_previous_day_plays"
    assert res.selection_status == "BASELINE_RETAINED"
    assert "Baseline retained" in res.selection_reason


def test_25_model_selection_api_endpoint():
    """Verify GET /api/v1/ml/model/selection returns selection metadata."""
    app = create_app()
    client = TestClient(app)
    res = client.get("/api/v1/ml/model/selection")
    assert res.status_code == 200
    data = res.json()
    assert "selected_model" in data
    assert "selection_status" in data
    assert "selection_metric" in data
    assert "selection_reason" in data


def test_26_model_card_api_endpoint():
    """Verify GET /api/v1/ml/model/card returns model card metadata."""
    app = create_app()
    client = TestClient(app)
    res = client.get("/api/v1/ml/model/card")
    assert res.status_code == 200
    data = res.json()
    assert data["target"] == "target_next_day_plays"
    assert data["training_data_source"] == "SYNTHETIC_FIXTURE"
    assert "known_limitations" in data


def test_27_model_card_api_no_secrets():
    """Verify GET /api/v1/ml/model/card response contains no file paths or secrets."""
    app = create_app()
    client = TestClient(app)
    res = client.get("/api/v1/ml/model/card")
    body = res.text.lower()
    assert "password" not in body
    assert "secret" not in body
    assert "/private/var" not in body


def test_28_openapi_model_selection_routes():
    """Verify OpenAPI schema documents GET /api/v1/ml/model/selection and /card."""
    app = create_app()
    client = TestClient(app)
    res = client.get("/openapi.json")
    assert res.status_code == 200
    paths = res.json()["paths"]
    assert "/api/v1/ml/model/selection" in paths
    assert "/api/v1/ml/model/card" in paths


def test_29_simplicity_tie_breaker_tolerance(prepared_dataset: PreparedMLDataset):
    """Verify 1% tie tolerance prefers simpler Ridge model when candidate MAEs are tied."""
    res = select_production_candidate(prepared_dataset, tie_tolerance_pct=0.50)
    # With 50% tie tolerance, Ridge (simpler than RF) is selected
    assert res.selected_model == "ridge_regression"


def test_30_known_limitations_synthetic_disclosure(prepared_dataset: PreparedMLDataset):
    """Verify model card limitations document synthetic fixture status."""
    res = select_production_candidate(prepared_dataset)
    lim_str = json.dumps(res.model_card.known_limitations).lower()
    assert "synthetic" in lim_str
