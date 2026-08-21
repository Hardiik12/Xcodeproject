"""Model persistence, SHA-256 verification, versioned package storage, and registry management."""

from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
from typing import Any, Dict, List, Optional, Union
import joblib

from app.core.config import get_settings
from app.ml.models.model_card import ModelCard
from app.ml.models.model_selector import FEATURE_SCHEMA_VERSION, ModelSelectionResult
from app.ml.models.training_dataset import PreparedMLDataset
from app.ml.preprocessing.pipeline import DEFAULT_CATEGORICAL_FEATURES, DEFAULT_NUMERIC_FEATURES
from app.ml.registry.exceptions import (
    ActiveModelUnavailableError,
    ModelArtifactIntegrityError,
    ModelChecksumError,
    ModelCompatibilityError,
    ModelIncompatibleError,
    ModelInvalidError,
    ModelNotFoundError,
    ModelRegistryError,
    ModelVersionAlreadyExistsError,
)
from app.ml.registry.schemas import (
    ChecksumManifest,
    ModelBundle,
    ModelMetadata,
    ModelRegistryMetadata,
    RegistryManifest,
)
from app.processing.data_validator import SENSITIVE_COLUMNS
from app.processing.errors import SensitiveDataError

CONTRACT_VERSION = "analytics-contract-v1"


def calculate_artifact_sha256(filepath: Union[str, Path]) -> str:
    """Calculate SHA-256 hex digest for a file on disk."""
    path = Path(filepath)
    if not path.exists() or not path.is_file():
        raise ModelNotFoundError(f"Artifact file not found on disk: {path}")

    hasher = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            hasher.update(chunk)
    return hasher.hexdigest()


class ModelRegistry:
    """Versioned model registry providing deterministic persistence, checksums, and safe loading."""

    def __init__(self, artifact_dir: Optional[Path] = None):
        settings = get_settings()
        if artifact_dir is not None:
            self.artifact_dir = Path(artifact_dir)
        else:
            self.artifact_dir = Path(getattr(settings, "MODEL_ARTIFACT_DIR", "artifacts/models"))

        self.artifact_dir.mkdir(parents=True, exist_ok=True)
        self.manifest_path = self.artifact_dir / "registry.json"
        self._state = "UNINITIALIZED"
        self._ensure_manifest()

    def _ensure_manifest(self) -> None:
        """Create registry manifest if missing."""
        if not self.manifest_path.exists():
            manifest = RegistryManifest(models=[])
            self._write_manifest(manifest)
            self._state = "NO_ACTIVE_MODEL"
        else:
            self._state = "READY"

    def _read_manifest(self) -> RegistryManifest:
        """Read and parse registry manifest."""
        if not self.manifest_path.exists():
            return RegistryManifest(models=[])
        try:
            with open(self.manifest_path, "r", encoding="utf-8") as f:
                data = json.load(f)
            return RegistryManifest(models=data.get("models", []))
        except Exception:
            return RegistryManifest(models=[])

    def _write_manifest(self, manifest: RegistryManifest) -> None:
        """Write registry manifest JSON."""
        with open(self.manifest_path, "w", encoding="utf-8") as f:
            json.dump({"models": manifest.models}, f, indent=2)

    def register_model(
        self,
        model_name: str,
        model_version: str,
        model_instance: Any,
        pipeline_instance: Any,
        prepared_dataset: PreparedMLDataset,
        selection_result: ModelSelectionResult,
        overwrite: bool = False,
    ) -> ModelRegistryMetadata:
        """Register a model instance into versioned package directory with checksums."""
        return self.save_model_selection_result(
            selection_result=selection_result,
            prepared_dataset=prepared_dataset,
            model_instance=model_instance,
            pipeline_instance=pipeline_instance,
            model_version=model_version,
            overwrite=overwrite,
        )

    def save_model_selection_result(
        self,
        selection_result: ModelSelectionResult,
        prepared_dataset: PreparedMLDataset,
        model_instance: Optional[Any] = None,
        pipeline_instance: Optional[Any] = None,
        model_version: Optional[str] = None,
        overwrite: bool = False,
    ) -> ModelRegistryMetadata:
        """Persist model selection result, joblib bundle, model card, and checksums."""
        model_name = "plays_predictor"
        version_str = model_version or f"{selection_result.selected_model}-v1"

        manifest = self._read_manifest()
        existing = [m for m in manifest.models if m.get("model_name") == model_name and m.get("model_version") == version_str]
        if existing and not overwrite:
            raise ModelVersionAlreadyExistsError(
                f"Model version '{version_str}' already exists in registry."
            )

        # Baseline Retained Check
        if selection_result.production_status == "BASELINE_RETAINED" or selection_result.selected_model == "naive_previous_day_plays":
            meta = ModelRegistryMetadata(
                model_name=model_name,
                model_version=version_str,
                algorithm="naive_previous_day_plays",
                target="target_next_day_plays",
                feature_schema_version=FEATURE_SCHEMA_VERSION,
                contract_version=CONTRACT_VERSION,
                created_at=datetime.now(timezone.utc).isoformat(),
                training_data_source=selection_result.model_card.training_data_source,
                training_row_count=prepared_dataset.train_rows,
                validation_row_count=prepared_dataset.val_rows,
                test_row_count=prepared_dataset.test_rows,
                selection_metric="Validation MAE",
                validation_metrics={
                    "mae": selection_result.validation_metrics.mae,
                    "rmse": selection_result.validation_metrics.rmse,
                    "r2": selection_result.validation_metrics.r2,
                },
                test_metrics={
                    "mae": selection_result.final_test_metrics.mae,
                    "rmse": selection_result.final_test_metrics.rmse,
                    "r2": selection_result.final_test_metrics.r2,
                },
                baseline_metrics={
                    "mae": selection_result.baseline_test_metrics.mae,
                    "rmse": selection_result.baseline_test_metrics.rmse,
                    "r2": selection_result.baseline_test_metrics.r2,
                },
                status="ACTIVE_BASELINE",
                artifact_path=None,
                artifact_sha256=None,
                model_card_path=None,
                model_card_sha256=None,
            )
            self._upsert_manifest(manifest, meta)
            return meta

        # Learned Model Candidate Persistence
        if model_instance is None or pipeline_instance is None:
            raise ModelRegistryError("Model instance and fitted pipeline instance are required to persist learned model bundle.")

        num_features = [col for col in prepared_dataset.X_train.columns if col in DEFAULT_NUMERIC_FEATURES]
        cat_features = [col for col in prepared_dataset.X_train.columns if col in DEFAULT_CATEGORICAL_FEATURES]

        bundle = ModelBundle(
            model=model_instance,
            preprocessor=pipeline_instance,
            model_name=model_name,
            model_version=version_str,
            algorithm=selection_result.selected_model,
            target="target_next_day_plays",
            feature_schema_version=FEATURE_SCHEMA_VERSION,
            contract_version=CONTRACT_VERSION,
            feature_names=prepared_dataset.feature_names,
            numeric_features=num_features,
            categorical_features=cat_features,
        )

        # Create versioned package directory: artifact_dir / version_str
        package_dir = self.artifact_dir / version_str
        package_dir.mkdir(parents=True, exist_ok=True)

        artifact_file = self.artifact_dir / f"{version_str}.joblib"
        pkg_model_file = package_dir / "model.joblib"
        pkg_prep_file = package_dir / "preprocessor.joblib"

        joblib.dump(bundle, artifact_file)
        joblib.dump(model_instance, pkg_model_file)
        joblib.dump(pipeline_instance, pkg_prep_file)

        art_hash = calculate_artifact_sha256(artifact_file)
        model_hash = calculate_artifact_sha256(pkg_model_file)
        prep_hash = calculate_artifact_sha256(pkg_prep_file)

        # Save Model Card JSON
        card_file = self.artifact_dir / f"{version_str}_card.json"
        pkg_card_file = package_dir / "model_card.json"
        card_data = {
            "model_name": selection_result.model_card.model_name,
            "algorithm": selection_result.model_card.algorithm,
            "target": selection_result.model_card.target,
            "feature_schema_version": selection_result.model_card.feature_schema_version,
            "training_data_source": selection_result.model_card.training_data_source,
            "selection_metric": selection_result.model_card.selection_metric,
            "selection_rule": selection_result.model_card.selection_rule,
            "validation_metrics": {
                "mae": selection_result.model_card.validation_metrics.mae,
                "rmse": selection_result.model_card.validation_metrics.rmse,
                "r2": selection_result.model_card.validation_metrics.r2,
            },
            "test_metrics": {
                "mae": selection_result.model_card.test_metrics.mae,
                "rmse": selection_result.model_card.test_metrics.rmse,
                "r2": selection_result.model_card.test_metrics.r2,
            },
            "known_limitations": selection_result.model_card.known_limitations,
            "production_status": selection_result.model_card.production_status,
        }
        with open(card_file, "w", encoding="utf-8") as f:
            json.dump(card_data, f, indent=2)
        with open(pkg_card_file, "w", encoding="utf-8") as f:
            json.dump(card_data, f, indent=2)

        card_hash = calculate_artifact_sha256(card_file)

        # Save Package metadata.json and checksums.json
        pkg_meta_file = package_dir / "metadata.json"
        meta_data = {
            "model_name": model_name,
            "model_version": version_str,
            "algorithm": selection_result.selected_model,
            "target": "target_next_day_plays",
            "feature_schema_version": FEATURE_SCHEMA_VERSION,
            "contract_version": CONTRACT_VERSION,
            "created_at": datetime.now(timezone.utc).isoformat(),
            "training_data_source": selection_result.model_card.training_data_source,
            "feature_count": len(prepared_dataset.feature_names),
            "artifact_files": ["model.joblib", "preprocessor.joblib", "model_card.json", "metadata.json"],
        }
        with open(pkg_meta_file, "w", encoding="utf-8") as f:
            json.dump(meta_data, f, indent=2)
        meta_hash = calculate_artifact_sha256(pkg_meta_file)

        checksum_file = package_dir / "checksums.json"
        checksum_data = {
            "algorithm": "SHA-256",
            "files": {
                "model.joblib": model_hash,
                "preprocessor.joblib": prep_hash,
                "model_card.json": card_hash,
                "metadata.json": meta_hash,
            },
        }
        with open(checksum_file, "w", encoding="utf-8") as f:
            json.dump(checksum_data, f, indent=2)

        # Build Metadata entry
        meta = ModelRegistryMetadata(
            model_name=model_name,
            model_version=version_str,
            algorithm=selection_result.selected_model,
            target="target_next_day_plays",
            feature_schema_version=FEATURE_SCHEMA_VERSION,
            contract_version=CONTRACT_VERSION,
            created_at=datetime.now(timezone.utc).isoformat(),
            training_data_source=selection_result.model_card.training_data_source,
            training_row_count=prepared_dataset.train_rows,
            validation_row_count=prepared_dataset.val_rows,
            test_row_count=prepared_dataset.test_rows,
            selection_metric="Validation MAE",
            validation_metrics={
                "mae": selection_result.validation_metrics.mae,
                "rmse": selection_result.validation_metrics.rmse,
                "r2": selection_result.validation_metrics.r2,
            },
            test_metrics={
                "mae": selection_result.final_test_metrics.mae,
                "rmse": selection_result.final_test_metrics.rmse,
                "r2": selection_result.final_test_metrics.r2,
            },
            baseline_metrics={
                "mae": selection_result.baseline_test_metrics.mae,
                "rmse": selection_result.baseline_test_metrics.rmse,
                "r2": selection_result.baseline_test_metrics.r2,
            },
            status="PROVISIONAL",
            artifact_path=str(artifact_file.resolve()),
            artifact_sha256=art_hash,
            model_card_path=str(card_file.resolve()),
            model_card_sha256=card_hash,
        )

        self._upsert_manifest(manifest, meta)
        return meta

    def _upsert_manifest(self, manifest: RegistryManifest, meta: ModelRegistryMetadata) -> None:
        """Insert or replace entry in registry manifest with PII checks."""
        entry = {
            "model_name": meta.model_name,
            "model_version": meta.model_version,
            "algorithm": meta.algorithm,
            "target": meta.target,
            "feature_schema_version": meta.feature_schema_version,
            "contract_version": meta.contract_version,
            "created_at": meta.created_at,
            "training_data_source": meta.training_data_source,
            "training_row_count": meta.training_row_count,
            "validation_row_count": meta.validation_row_count,
            "test_row_count": meta.test_row_count,
            "selection_metric": meta.selection_metric,
            "validation_metrics": meta.validation_metrics,
            "test_metrics": meta.test_metrics,
            "baseline_metrics": meta.baseline_metrics,
            "status": meta.status,
            "artifact_path": meta.artifact_path,
            "artifact_sha256": meta.artifact_sha256,
            "model_card_path": meta.model_card_path,
            "model_card_sha256": meta.model_card_sha256,
        }

        # Check for secrets/PII in metadata values and model card
        text_summary = json.dumps(entry).lower()
        if meta.model_card_path and Path(meta.model_card_path).exists():
            text_summary += " " + Path(meta.model_card_path).read_text(encoding="utf-8").lower()

        for pii_col in SENSITIVE_COLUMNS:
            pii_lower = pii_col.lower()
            if pii_lower == "name":
                if '"name":' in text_summary or ' name ' in text_summary:
                    raise SensitiveDataError(f"PII field '{pii_col}' detected in registry metadata payload!")
            elif pii_lower in text_summary:
                raise SensitiveDataError(f"PII field '{pii_col}' detected in registry metadata payload!")

        filtered = [m for m in manifest.models if not (m.get("model_name") == meta.model_name and m.get("model_version") == meta.model_version)]
        filtered.append(entry)
        manifest.models = filtered
        self._write_manifest(manifest)

    def get_model(self, model_name: str, model_version: str) -> Dict[str, Any]:
        """Retrieve registered metadata entry by name and version."""
        manifest = self._read_manifest()
        for m in manifest.models:
            if m.get("model_name") == model_name and m.get("model_version") == model_version:
                return m
        raise ModelNotFoundError(f"Model '{model_name}' version '{model_version}' not found in registry.")

    def get_active_model(self, model_name: str = "plays_predictor") -> Optional[Dict[str, Any]]:
        """Retrieve active model metadata entry."""
        manifest = self._read_manifest()
        matching = [m for m in manifest.models if m.get("model_name") == model_name]
        if not matching:
            return None

        # Prefer explicitly ACTIVE entries first
        active_entries = [m for m in matching if m.get("status") == "ACTIVE"]
        if active_entries:
            return active_entries[-1]

        # Otherwise fallback to ACTIVE_BASELINE or PROVISIONAL entries
        fallback_entries = [m for m in matching if m.get("status") in {"ACTIVE_BASELINE", "PROVISIONAL"}]
        if not fallback_entries:
            return None
        return fallback_entries[-1]

    def activate_model(self, model_name: str, model_version: str) -> Dict[str, Any]:
        """Atomically set model status to ACTIVE after verification."""
        entry = self.get_model(model_name, model_version)

        # Verification check before activation
        valid_res = self.verify_model(model_name, model_version)
        if valid_res.get("status") != "VALID":
            manifest = self._read_manifest()
            for m in manifest.models:
                if m.get("model_name") == model_name and m.get("model_version") == model_version:
                    m["status"] = "MODEL_INVALID"
            self._write_manifest(manifest)
            raise ModelInvalidError(f"Cannot activate invalid model version '{model_version}'.")

        manifest = self._read_manifest()
        for m in manifest.models:
            if m.get("model_name") == model_name:
                if m.get("model_version") == model_version:
                    m["status"] = "ACTIVE"
                elif m.get("status") == "ACTIVE":
                    m["status"] = "ARCHIVED"

        self._write_manifest(manifest)
        return self.get_model(model_name, model_version)

    def list_models(self) -> List[Dict[str, Any]]:
        """List all registered model entries."""
        manifest = self._read_manifest()
        return manifest.models

    def verify_model(self, model_name: str, model_version: str) -> Dict[str, Any]:
        """Verify checksums, artifact existence, and schema compatibility."""
        try:
            entry = self.get_model(model_name, model_version)
            status = entry.get("status")

            if status == "ACTIVE_BASELINE":
                return {"status": "VALID", "model_name": model_name, "model_version": model_version}

            art_path = entry.get("artifact_path")
            if not art_path or not Path(art_path).exists():
                return {"status": "INVALID", "reason": "Artifact file missing"}

            expected_hash = entry.get("artifact_sha256")
            actual_hash = calculate_artifact_sha256(art_path)
            if expected_hash and actual_hash != expected_hash:
                return {"status": "INVALID", "reason": "Checksum mismatch"}

            if entry.get("feature_schema_version") != FEATURE_SCHEMA_VERSION:
                return {"status": "INVALID", "reason": "Feature schema mismatch"}

            if entry.get("contract_version") != CONTRACT_VERSION:
                return {"status": "INVALID", "reason": "Contract version mismatch"}

            if entry.get("target") != "target_next_day_plays":
                return {"status": "INVALID", "reason": "Target mismatch"}

            return {"status": "VALID", "model_name": model_name, "model_version": model_version}
        except Exception as exc:
            return {"status": "INVALID", "reason": str(exc)}

    def load_model(self, model_name: str, model_version: str) -> ModelBundle:
        """Alias for load_model_bundle."""
        return self.load_model_bundle(model_name, model_version)

    def load_model_bundle(self, model_name: str, model_version: str) -> ModelBundle:
        """Load and verify joblib model bundle from registry."""
        entry = self.get_model(model_name, model_version)
        status = entry.get("status")

        if status == "ACTIVE_BASELINE":
            raise ModelNotFoundError(
                f"Model '{model_name}' version '{model_version}' is registered as ACTIVE_BASELINE without binary artifact."
            )

        if entry.get("feature_schema_version") != FEATURE_SCHEMA_VERSION:
            raise ModelCompatibilityError(
                f"Feature schema mismatch: registered '{entry.get('feature_schema_version')}' != '{FEATURE_SCHEMA_VERSION}'."
            )

        if entry.get("contract_version") != CONTRACT_VERSION:
            raise ModelCompatibilityError(
                f"Contract version mismatch: registered '{entry.get('contract_version')}' != '{CONTRACT_VERSION}'."
            )

        if entry.get("target") != "target_next_day_plays":
            raise ModelCompatibilityError(
                f"Target mismatch: registered '{entry.get('target')}' != 'target_next_day_plays'."
            )

        art_path_str = entry.get("artifact_path")
        if not art_path_str:
            raise ModelNotFoundError(f"No artifact path registered for '{model_version}'.")

        art_path = Path(art_path_str)
        if not art_path.exists():
            raise ModelNotFoundError(f"Artifact file not found on disk: {art_path}")

        # Checksum Verification
        expected_hash = entry.get("artifact_sha256")
        actual_hash = calculate_artifact_sha256(art_path)
        if expected_hash and actual_hash != expected_hash:
            raise ModelArtifactIntegrityError(
                f"SHA-256 integrity check failed for '{model_version}'. Expected {expected_hash}, got {actual_hash}."
            )

        try:
            bundle = joblib.load(art_path)
        except Exception as exc:
            raise ModelInvalidError(f"Failed to deserialize joblib bundle: {str(exc)}") from exc

        if not isinstance(bundle, ModelBundle):
            raise ModelInvalidError(f"Deserialized artifact is not a valid ModelBundle instance.")

        return bundle
