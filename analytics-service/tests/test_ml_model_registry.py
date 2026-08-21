"""Tests for Phase 7.6 Checkpoint 9: Model Persistence & Versioned Registry."""

from datetime import date, timedelta
from pathlib import Path
import json
import numpy as np
import pandas as pd
import pytest

from app.ml.features.feature_builder import build_ml_features
from app.ml.models.model_card import ModelCard
from app.ml.models.model_selector import select_production_candidate
from app.ml.models.model_trainer import ModelTrainer
from app.ml.models.training_dataset import PreparedMLDataset, prepare_training_dataset
from app.ml.registry import (
    ModelArtifactIntegrityError,
    ModelBundle,
    ModelCompatibilityError,
    ModelNotFoundError,
    ModelRegistry,
    ModelVersionAlreadyExistsError,
    calculate_artifact_sha256,
)
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


@pytest.fixture
def selection_and_instances(prepared_dataset):
    """Run selection and train model instance for testing registry save/load."""
    sel = select_production_candidate(prepared_dataset)
    trainer = ModelTrainer(random_state=42)
    train_res = trainer.train_and_evaluate(prepared_dataset)
    return sel, train_res.model_instance, train_res.pipeline_instance


def test_1_registry_creation(tmp_path: Path):
    """Verify ModelRegistry initializes manifest and artifact directory."""
    reg = ModelRegistry(artifact_dir=tmp_path)
    assert tmp_path.exists()
    assert (tmp_path / "registry.json").exists()


def test_2_model_registration(tmp_path: Path, prepared_dataset, selection_and_instances):
    """Verify save_model_selection_result registers model metadata."""
    sel, model, pipe = selection_and_instances
    reg = ModelRegistry(artifact_dir=tmp_path)
    meta = reg.save_model_selection_result(
        selection_result=sel,
        prepared_dataset=prepared_dataset,
        model_instance=model,
        pipeline_instance=pipe,
        model_version="plays-predictor-v1",
    )
    assert meta.model_name == "plays_predictor"
    assert meta.model_version == "plays-predictor-v1"


def test_3_model_versioning(tmp_path: Path, prepared_dataset, selection_and_instances):
    """Verify version identifier is correctly written to manifest."""
    sel, model, pipe = selection_and_instances
    reg = ModelRegistry(artifact_dir=tmp_path)
    meta = reg.save_model_selection_result(
        selection_result=sel,
        prepared_dataset=prepared_dataset,
        model_instance=model,
        pipeline_instance=pipe,
        model_version="plays-predictor-v1",
    )
    models = reg.list_models()
    assert len(models) == 1
    assert models[0]["model_version"] == "plays-predictor-v1"


def test_4_save_artifact(tmp_path: Path, prepared_dataset, selection_and_instances):
    """Verify binary .joblib and JSON model card files are saved to disk."""
    sel, model, pipe = selection_and_instances
    reg = ModelRegistry(artifact_dir=tmp_path)
    meta = reg.save_model_selection_result(
        selection_result=sel,
        prepared_dataset=prepared_dataset,
        model_instance=model,
        pipeline_instance=pipe,
        model_version="plays-predictor-v1",
    )
    assert Path(meta.artifact_path).exists()
    assert Path(meta.model_card_path).exists()


def test_5_load_artifact(tmp_path: Path, prepared_dataset, selection_and_instances):
    """Verify load_model_bundle returns valid ModelBundle."""
    sel, model, pipe = selection_and_instances
    reg = ModelRegistry(artifact_dir=tmp_path)
    reg.save_model_selection_result(
        selection_result=sel,
        prepared_dataset=prepared_dataset,
        model_instance=model,
        pipeline_instance=pipe,
        model_version="plays-predictor-v1",
    )
    bundle = reg.load_model_bundle("plays_predictor", "plays-predictor-v1")
    assert isinstance(bundle, ModelBundle)
    assert bundle.algorithm == "ridge_regression"


def test_6_save_load_prediction_equivalence(tmp_path: Path, prepared_dataset, selection_and_instances):
    """Verify pre-save predictions match post-load bundle predictions exactly."""
    sel, model, pipe = selection_and_instances
    reg = ModelRegistry(artifact_dir=tmp_path)
    reg.save_model_selection_result(
        selection_result=sel,
        prepared_dataset=prepared_dataset,
        model_instance=model,
        pipeline_instance=pipe,
        model_version="plays-predictor-v1",
    )
    
    # Pre-save predictions
    X_proc = pipe.transform(prepared_dataset.X_val)
    preds_pre = model.predict(X_proc)

    # Post-load predictions
    bundle = reg.load_model_bundle("plays_predictor", "plays-predictor-v1")
    preds_post = bundle.predict(prepared_dataset.X_val)

    np.testing.assert_allclose(preds_pre, preds_post, rtol=1e-7, atol=1e-7)


def test_7_sha256_calculation(tmp_path: Path):
    """Verify calculate_artifact_sha256 computes deterministic 64-char hex string."""
    dummy_file = tmp_path / "test.txt"
    dummy_file.write_text("CommunityOTT Analytics ML Pipeline 7.6", encoding="utf-8")
    hash1 = calculate_artifact_sha256(dummy_file)
    hash2 = calculate_artifact_sha256(dummy_file)

    assert len(hash1) == 64
    assert hash1 == hash2


def test_8_sha256_verification_on_load(tmp_path: Path, prepared_dataset, selection_and_instances):
    """Verify loading verifies SHA-256 against registry metadata."""
    sel, model, pipe = selection_and_instances
    reg = ModelRegistry(artifact_dir=tmp_path)
    meta = reg.save_model_selection_result(
        selection_result=sel,
        prepared_dataset=prepared_dataset,
        model_instance=model,
        pipeline_instance=pipe,
        model_version="plays-predictor-v1",
    )
    assert meta.artifact_sha256 == calculate_artifact_sha256(meta.artifact_path)


def test_9_corrupted_artifact_rejection(tmp_path: Path, prepared_dataset, selection_and_instances):
    """Verify ModelArtifactIntegrityError is raised if artifact file is tampered with."""
    sel, model, pipe = selection_and_instances
    reg = ModelRegistry(artifact_dir=tmp_path)
    meta = reg.save_model_selection_result(
        selection_result=sel,
        prepared_dataset=prepared_dataset,
        model_instance=model,
        pipeline_instance=pipe,
        model_version="plays-predictor-v1",
    )

    # Corrupt artifact file
    art_path = Path(meta.artifact_path)
    with open(art_path, "ab") as f:
        f.write(b"\x00\x00CORRUPT_BYTES\x00\x00")

    with pytest.raises(ModelArtifactIntegrityError, match="SHA-256 integrity check failed"):
        reg.load_model_bundle("plays_predictor", "plays-predictor-v1")


def test_10_missing_artifact_rejection(tmp_path: Path, prepared_dataset, selection_and_instances):
    """Verify ModelNotFoundError is raised if artifact file is deleted."""
    sel, model, pipe = selection_and_instances
    reg = ModelRegistry(artifact_dir=tmp_path)
    meta = reg.save_model_selection_result(
        selection_result=sel,
        prepared_dataset=prepared_dataset,
        model_instance=model,
        pipeline_instance=pipe,
        model_version="plays-predictor-v1",
    )

    # Delete artifact
    Path(meta.artifact_path).unlink()

    with pytest.raises(ModelNotFoundError, match="file not found on disk"):
        reg.load_model_bundle("plays_predictor", "plays-predictor-v1")


def test_11_version_collision_rejection(tmp_path: Path, prepared_dataset, selection_and_instances):
    """Verify ModelVersionAlreadyExistsError is raised when saving duplicate version without overwrite."""
    sel, model, pipe = selection_and_instances
    reg = ModelRegistry(artifact_dir=tmp_path)
    reg.save_model_selection_result(
        selection_result=sel,
        prepared_dataset=prepared_dataset,
        model_instance=model,
        pipeline_instance=pipe,
        model_version="plays-predictor-v1",
    )

    with pytest.raises(ModelVersionAlreadyExistsError, match="already exists in registry"):
        reg.save_model_selection_result(
            selection_result=sel,
            prepared_dataset=prepared_dataset,
            model_instance=model,
            pipeline_instance=pipe,
            model_version="plays-predictor-v1",
            overwrite=False,
        )


def test_12_list_models(tmp_path: Path, prepared_dataset, selection_and_instances):
    """Verify list_models returns registered version entries."""
    sel, model, pipe = selection_and_instances
    reg = ModelRegistry(artifact_dir=tmp_path)
    reg.save_model_selection_result(
        selection_result=sel,
        prepared_dataset=prepared_dataset,
        model_instance=model,
        pipeline_instance=pipe,
        model_version="plays-predictor-v1",
    )
    models = reg.list_models()
    assert len(models) == 1
    assert models[0]["model_name"] == "plays_predictor"


def test_13_active_model_lookup(tmp_path: Path, prepared_dataset, selection_and_instances):
    """Verify get_active_model retrieves latest registered model metadata."""
    sel, model, pipe = selection_and_instances
    reg = ModelRegistry(artifact_dir=tmp_path)
    reg.save_model_selection_result(
        selection_result=sel,
        prepared_dataset=prepared_dataset,
        model_instance=model,
        pipeline_instance=pipe,
        model_version="plays-predictor-v1",
    )
    active = reg.get_active_model("plays_predictor")
    assert active is not None
    assert active["model_version"] == "plays-predictor-v1"


def test_14_metadata_validation(tmp_path: Path, prepared_dataset, selection_and_instances):
    """Verify registered metadata contains required metrics and counts."""
    sel, model, pipe = selection_and_instances
    reg = ModelRegistry(artifact_dir=tmp_path)
    meta = reg.save_model_selection_result(
        selection_result=sel,
        prepared_dataset=prepared_dataset,
        model_instance=model,
        pipeline_instance=pipe,
        model_version="plays-predictor-v1",
    )
    assert meta.training_row_count == prepared_dataset.train_rows
    assert "mae" in meta.validation_metrics


def test_15_feature_schema_compatibility(tmp_path: Path, prepared_dataset, selection_and_instances):
    """Verify ModelCompatibilityError is raised if bundle feature_schema_version mismatches manifest."""
    sel, model, pipe = selection_and_instances
    reg = ModelRegistry(artifact_dir=tmp_path)
    meta = reg.save_model_selection_result(
        selection_result=sel,
        prepared_dataset=prepared_dataset,
        model_instance=model,
        pipeline_instance=pipe,
        model_version="plays-predictor-v1",
    )

    # Mutate schema in manifest
    manifest_data = json.loads(Path(reg.manifest_path).read_text())
    manifest_data["models"][0]["feature_schema_version"] = "features-v999"
    Path(reg.manifest_path).write_text(json.dumps(manifest_data), encoding="utf-8")

    with pytest.raises(ModelCompatibilityError, match="Feature schema mismatch"):
        reg.load_model_bundle("plays_predictor", "plays-predictor-v1")


def test_16_target_compatibility(tmp_path: Path, prepared_dataset, selection_and_instances):
    """Verify ModelCompatibilityError is raised if target name mismatches manifest."""
    sel, model, pipe = selection_and_instances
    reg = ModelRegistry(artifact_dir=tmp_path)
    meta = reg.save_model_selection_result(
        selection_result=sel,
        prepared_dataset=prepared_dataset,
        model_instance=model,
        pipeline_instance=pipe,
        model_version="plays-predictor-v1",
    )

    manifest_data = json.loads(Path(reg.manifest_path).read_text())
    manifest_data["models"][0]["target"] = "target_other"
    Path(reg.manifest_path).write_text(json.dumps(manifest_data), encoding="utf-8")

    with pytest.raises(ModelCompatibilityError, match="Target mismatch"):
        reg.load_model_bundle("plays_predictor", "plays-predictor-v1")


def test_17_model_card_persistence(tmp_path: Path, prepared_dataset, selection_and_instances):
    """Verify model card JSON is saved alongside joblib bundle."""
    sel, model, pipe = selection_and_instances
    reg = ModelRegistry(artifact_dir=tmp_path)
    meta = reg.save_model_selection_result(
        selection_result=sel,
        prepared_dataset=prepared_dataset,
        model_instance=model,
        pipeline_instance=pipe,
        model_version="plays-predictor-v1",
    )
    card_path = Path(meta.model_card_path)
    assert card_path.exists()

    card_json = json.loads(card_path.read_text(encoding="utf-8"))
    assert card_json["target"] == "target_next_day_plays"


def test_18_baseline_registration_without_binary_artifact(tmp_path: Path, prepared_dataset):
    """Verify BASELINE_RETAINED status registers metadata without creating binary joblib file."""
    # Force baseline retained selection result
    sel = select_production_candidate(prepared_dataset)
    sel.production_status = "BASELINE_RETAINED"
    sel.selected_model = "naive_previous_day_plays"

    reg = ModelRegistry(artifact_dir=tmp_path)
    meta = reg.save_model_selection_result(
        selection_result=sel,
        prepared_dataset=prepared_dataset,
        model_version="plays-baseline-v1",
    )

    assert meta.status == "ACTIVE_BASELINE"
    assert meta.artifact_path is None
    assert meta.artifact_sha256 is None

    # Verification loading raises ModelNotFoundError for binary load
    with pytest.raises(ModelNotFoundError, match="registered as ACTIVE_BASELINE without binary artifact"):
        reg.load_model_bundle("plays_predictor", "plays-baseline-v1")


def test_19_no_pii_in_registry_metadata(tmp_path: Path, prepared_dataset, selection_and_instances):
    """Verify SensitiveDataError is raised if PII is injected into metadata."""
    sel, model, pipe = selection_and_instances
    sel.model_card.known_limitations.append("contains email user@example.com")

    reg = ModelRegistry(artifact_dir=tmp_path)
    with pytest.raises(SensitiveDataError, match="PII field 'email' detected"):
        reg.save_model_selection_result(
            selection_result=sel,
            prepared_dataset=prepared_dataset,
            model_instance=model,
            pipeline_instance=pipe,
            model_version="plays-predictor-v1",
        )


def test_20_deterministic_loading(tmp_path: Path, prepared_dataset, selection_and_instances):
    """Verify bundle loading is deterministic across multiple calls."""
    sel, model, pipe = selection_and_instances
    reg = ModelRegistry(artifact_dir=tmp_path)
    reg.save_model_selection_result(
        selection_result=sel,
        prepared_dataset=prepared_dataset,
        model_instance=model,
        pipeline_instance=pipe,
        model_version="plays-predictor-v1",
    )

    b1 = reg.load_model_bundle("plays_predictor", "plays-predictor-v1")
    b2 = reg.load_model_bundle("plays_predictor", "plays-predictor-v1")

    p1 = b1.predict(prepared_dataset.X_val)
    p2 = b2.predict(prepared_dataset.X_val)

    np.testing.assert_allclose(p1, p2, rtol=1e-7, atol=1e-7)


def test_21_empty_registry(tmp_path: Path):
    """Verify empty registry manifest returns empty list."""
    reg = ModelRegistry(artifact_dir=tmp_path)
    assert reg.list_models() == []
    assert reg.get_active_model("plays_predictor") is None


def test_22_invalid_model_version(tmp_path: Path):
    """Verify load_model_bundle for non-existent version raises ModelNotFoundError."""
    reg = ModelRegistry(artifact_dir=tmp_path)
    with pytest.raises(ModelNotFoundError, match="not found in registry"):
        reg.load_model_bundle("plays_predictor", "non_existent_version")


def test_23_invalid_metadata_handling(tmp_path: Path):
    """Verify missing manifest file creates new empty manifest."""
    reg = ModelRegistry(artifact_dir=tmp_path)
    (tmp_path / "registry.json").unlink()
    assert reg.list_models() == []


def test_24_artifact_path_safety(tmp_path: Path, prepared_dataset, selection_and_instances):
    """Verify all saved files reside strictly within configured artifact_dir."""
    sel, model, pipe = selection_and_instances
    reg = ModelRegistry(artifact_dir=tmp_path)
    meta = reg.save_model_selection_result(
        selection_result=sel,
        prepared_dataset=prepared_dataset,
        model_instance=model,
        pipeline_instance=pipe,
        model_version="plays-predictor-v1",
    )

    assert Path(meta.artifact_path).resolve().is_relative_to(tmp_path.resolve())
    assert Path(meta.model_card_path).resolve().is_relative_to(tmp_path.resolve())


def test_25_checksums_json_package_structure(tmp_path: Path, prepared_dataset, selection_and_instances):
    """Verify package directory contains model.joblib, preprocessor.joblib, metadata.json, and checksums.json."""
    sel, model, pipe = selection_and_instances
    reg = ModelRegistry(artifact_dir=tmp_path)
    reg.save_model_selection_result(
        selection_result=sel,
        prepared_dataset=prepared_dataset,
        model_instance=model,
        pipeline_instance=pipe,
        model_version="plays-predictor-v1",
    )

    pkg_dir = tmp_path / "plays-predictor-v1"
    assert pkg_dir.exists()
    assert (pkg_dir / "model.joblib").exists()
    assert (pkg_dir / "preprocessor.joblib").exists()
    assert (pkg_dir / "model_card.json").exists()
    assert (pkg_dir / "metadata.json").exists()
    assert (pkg_dir / "checksums.json").exists()

    checksum_data = json.loads((pkg_dir / "checksums.json").read_text())
    assert checksum_data["algorithm"] == "SHA-256"
    assert "model.joblib" in checksum_data["files"]


def test_26_activate_model_success(tmp_path: Path, prepared_dataset, selection_and_instances):
    """Verify activate_model sets status to ACTIVE."""
    sel, model, pipe = selection_and_instances
    reg = ModelRegistry(artifact_dir=tmp_path)
    reg.save_model_selection_result(
        selection_result=sel,
        prepared_dataset=prepared_dataset,
        model_instance=model,
        pipeline_instance=pipe,
        model_version="plays-predictor-v1",
    )

    meta = reg.activate_model("plays_predictor", "plays-predictor-v1")
    assert meta["status"] == "ACTIVE"


def test_27_activate_model_failure_preserves_previous(tmp_path: Path, prepared_dataset, selection_and_instances):
    """Verify failed activation leaves previous valid model active."""
    sel, model, pipe = selection_and_instances
    reg = ModelRegistry(artifact_dir=tmp_path)

    # Version 1 valid
    reg.save_model_selection_result(
        selection_result=sel,
        prepared_dataset=prepared_dataset,
        model_instance=model,
        pipeline_instance=pipe,
        model_version="plays-predictor-v1",
    )
    reg.activate_model("plays_predictor", "plays-predictor-v1")

    # Version 2 corrupt
    meta2 = reg.save_model_selection_result(
        selection_result=sel,
        prepared_dataset=prepared_dataset,
        model_instance=model,
        pipeline_instance=pipe,
        model_version="plays-predictor-v2",
        overwrite=True,
    )
    Path(meta2.artifact_path).unlink()

    from app.ml.registry.exceptions import ModelInvalidError
    with pytest.raises(ModelInvalidError, match="Cannot activate invalid model"):
        reg.activate_model("plays_predictor", "plays-predictor-v2")

    # Version 1 remains active
    active = reg.get_active_model("plays_predictor")
    assert active["model_version"] == "plays-predictor-v1"
    assert active["status"] == "ACTIVE"


def test_28_verify_model_valid(tmp_path: Path, prepared_dataset, selection_and_instances):
    """Verify verify_model returns status VALID for valid package."""
    sel, model, pipe = selection_and_instances
    reg = ModelRegistry(artifact_dir=tmp_path)
    reg.save_model_selection_result(
        selection_result=sel,
        prepared_dataset=prepared_dataset,
        model_instance=model,
        pipeline_instance=pipe,
        model_version="plays-predictor-v1",
    )

    ver = reg.verify_model("plays_predictor", "plays-predictor-v1")
    assert ver["status"] == "VALID"


def test_29_verify_model_invalid_file_missing(tmp_path: Path, prepared_dataset, selection_and_instances):
    """Verify verify_model returns status INVALID if artifact is missing."""
    sel, model, pipe = selection_and_instances
    reg = ModelRegistry(artifact_dir=tmp_path)
    meta = reg.save_model_selection_result(
        selection_result=sel,
        prepared_dataset=prepared_dataset,
        model_instance=model,
        pipeline_instance=pipe,
        model_version="plays-predictor-v1",
    )

    Path(meta.artifact_path).unlink()
    ver = reg.verify_model("plays_predictor", "plays-predictor-v1")
    assert ver["status"] == "INVALID"


def test_30_verify_model_invalid_checksum(tmp_path: Path, prepared_dataset, selection_and_instances):
    """Verify verify_model returns status INVALID if SHA-256 checksum mismatches."""
    sel, model, pipe = selection_and_instances
    reg = ModelRegistry(artifact_dir=tmp_path)
    meta = reg.save_model_selection_result(
        selection_result=sel,
        prepared_dataset=prepared_dataset,
        model_instance=model,
        pipeline_instance=pipe,
        model_version="plays-predictor-v1",
    )

    with open(meta.artifact_path, "ab") as f:
        f.write(b"CORRUPTED_TAMPERED")

    ver = reg.verify_model("plays_predictor", "plays-predictor-v1")
    assert ver["status"] == "INVALID"


def test_31_registry_api_endpoint(tmp_path: Path):
    """Verify GET /api/v1/ml/registry returns registry metadata."""
    from fastapi.testclient import TestClient
    from app.main import create_app

    app = create_app()
    client = TestClient(app)
    res = client.get("/api/v1/ml/registry")
    assert res.status_code == 200
    data = res.json()
    assert "active_model" in data
    assert "status" in data


def test_32_registry_verify_api_endpoint(tmp_path: Path):
    """Verify GET /api/v1/ml/registry/verify returns valid or invalid status."""
    from fastapi.testclient import TestClient
    from app.main import create_app

    app = create_app()
    client = TestClient(app)
    res = client.get("/api/v1/ml/registry/verify")
    assert res.status_code in [200, 503]


def test_33_contract_version_mismatch_rejection(tmp_path: Path, prepared_dataset, selection_and_instances):
    """Verify load_model_bundle raises ModelCompatibilityError if contract version mismatches."""
    sel, model, pipe = selection_and_instances
    reg = ModelRegistry(artifact_dir=tmp_path)
    reg.save_model_selection_result(
        selection_result=sel,
        prepared_dataset=prepared_dataset,
        model_instance=model,
        pipeline_instance=pipe,
        model_version="plays-predictor-v1",
    )

    manifest_data = json.loads(Path(reg.manifest_path).read_text())
    manifest_data["models"][0]["contract_version"] = "analytics-contract-v99"
    Path(reg.manifest_path).write_text(json.dumps(manifest_data))

    with pytest.raises(ModelCompatibilityError, match="Contract version mismatch"):
        reg.load_model_bundle("plays_predictor", "plays-predictor-v1")


def test_34_no_arbitrary_path_allowed(tmp_path: Path):
    """Verify registry methods accept model_name/model_version rather than arbitrary file paths."""
    reg = ModelRegistry(artifact_dir=tmp_path)
    with pytest.raises(ModelNotFoundError):
        reg.load_model_bundle("../../etc/passwd", "v1")


def test_35_metadata_json_fields(tmp_path: Path, prepared_dataset, selection_and_instances):
    """Verify metadata.json in package directory contains feature_count and contract_version."""
    sel, model, pipe = selection_and_instances
    reg = ModelRegistry(artifact_dir=tmp_path)
    reg.save_model_selection_result(
        selection_result=sel,
        prepared_dataset=prepared_dataset,
        model_instance=model,
        pipeline_instance=pipe,
        model_version="plays-predictor-v1",
    )

    meta_file = tmp_path / "plays-predictor-v1" / "metadata.json"
    meta_json = json.loads(meta_file.read_text())

    assert meta_json["contract_version"] == "analytics-contract-v1"
    assert meta_json["feature_schema_version"] == "features-v1"
    assert meta_json["feature_count"] > 0


def test_36_openapi_registry_routes():
    """Verify OpenAPI schema documents GET /api/v1/ml/registry and /registry/verify."""
    from fastapi.testclient import TestClient
    from app.main import create_app

    app = create_app()
    client = TestClient(app)
    res = client.get("/openapi.json")
    assert res.status_code == 200
    paths = res.json()["paths"]
    assert "/api/v1/ml/registry" in paths
    assert "/api/v1/ml/registry/verify" in paths


def test_37_custom_exceptions_exported():
    """Verify all custom registry exceptions are importable from app.ml.registry."""
    from app.ml.registry import (
        ActiveModelUnavailableError,
        ModelChecksumError,
        ModelIncompatibleError,
        ModelInvalidError,
    )
    assert issubclass(ActiveModelUnavailableError, Exception)
    assert issubclass(ModelInvalidError, Exception)
    assert issubclass(ModelIncompatibleError, Exception)
    assert issubclass(ModelChecksumError, Exception)


def test_38_calculate_artifact_sha256_missing_file_raises_error(tmp_path: Path):
    """Verify calculate_artifact_sha256 raises ModelNotFoundError for non-existent file."""
    with pytest.raises(ModelNotFoundError, match="not found on disk"):
        calculate_artifact_sha256(tmp_path / "non_existent_file.bin")

