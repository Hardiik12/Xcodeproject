"""Prediction service for batch inference, model loading, and readiness validation."""

import math
from pathlib import Path
import time
from typing import Dict, List, Optional, Tuple
import numpy as np
import pandas as pd

from app.ml.preprocessing.pipeline import DEFAULT_CATEGORICAL_FEATURES, DEFAULT_NUMERIC_FEATURES
from app.ml.registry import (
    ModelArtifactIntegrityError,
    ModelCompatibilityError,
    ModelIncompatibleError,
    ModelNotFoundError,
    ModelRegistry,
)
from app.ml.registry.schemas import (
    ModelBundle,
    ModelRegistryMetadata,
)
from app.ml.serving.metrics import metrics_tracker
from app.ml.serving.schemas import (
    ModelStatusResponse,
    PredictionRequest,
    PredictionResponse,
    PredictionResultItem,
    ReadinessResponse,
)


class ModelNotReadyError(Exception):
    """Raised when predictions are requested while the model service is NOT_READY."""

    def __init__(self, reason: str = "MODEL_UNAVAILABLE"):
        self.reason = reason
        super().__init__(f"Prediction service is NOT_READY: {reason}")


class ModelOutputInvalidError(Exception):
    """Raised when model produces NaN, Infinity, or unhandled invalid output values."""

    pass


class PredictionService:
    """Manages active ML model lifecycle, readiness probes, and prediction serving."""

    def __init__(self, artifact_dir: Optional[Path] = None, model_name: str = "plays_predictor"):
        self.model_name = model_name
        self.registry = ModelRegistry(artifact_dir=artifact_dir)
        self._cached_bundle: Optional[ModelBundle] = None
        self._cached_metadata: Optional[ModelRegistryMetadata] = None
        self._is_baseline_mode: bool = False

    def initialize(self) -> None:
        """Attempt to load active model from registry at startup."""
        try:
            self._load_active_model()
        except Exception:
            self._cached_bundle = None
            self._cached_metadata = None
            self._is_baseline_mode = False

    def _load_active_model(self) -> Tuple[Optional[ModelBundle], Optional[ModelRegistryMetadata]]:
        """Internal helper to load and validate active model from registry."""
        active_entry = self.registry.get_active_model(self.model_name)
        if not active_entry:
            self._cached_bundle = None
            self._cached_metadata = None
            self._is_baseline_mode = False
            return None, None

        version = active_entry["model_version"]
        status = active_entry.get("status")

        if status == "ACTIVE_BASELINE":
            self._is_baseline_mode = True
            self._cached_bundle = None
            manifest = self.registry._read_manifest()
            meta_dict = [m for m in manifest.models if m.get("model_version") == version][0]
            self._cached_metadata = ModelRegistryMetadata(**meta_dict)
            return None, self._cached_metadata

        # Learned model load
        bundle = self.registry.load_model_bundle(self.model_name, version)
        manifest = self.registry._read_manifest()
        meta_dict = [m for m in manifest.models if m.get("model_version") == version][0]
        meta = ModelRegistryMetadata(**meta_dict)

        self._cached_bundle = bundle
        self._cached_metadata = meta
        self._is_baseline_mode = False
        return bundle, meta

    def get_readiness(self) -> ReadinessResponse:
        """Evaluate readiness status safely without exposing secrets or paths."""
        try:
            active_entry = self.registry.get_active_model(self.model_name)
            if not active_entry:
                return ReadinessResponse(status="NOT_READY", reason="MODEL_UNAVAILABLE")

            version = active_entry["model_version"]
            status = active_entry.get("status")

            if status == "ACTIVE_BASELINE":
                return ReadinessResponse(
                    status="READY",
                    model=self.model_name,
                    model_version=version,
                )

            # Verify bundle and metadata integrity
            if self._cached_bundle and self._cached_metadata:
                bundle, meta = self._cached_bundle, self._cached_metadata
            else:
                bundle, meta = self._load_active_model()

            if not bundle or not meta:
                return ReadinessResponse(status="NOT_READY", reason="MODEL_UNAVAILABLE")

            if meta.feature_schema_version != "features-v1":
                return ReadinessResponse(status="NOT_READY", reason="FEATURE_SCHEMA_MISMATCH")

            if meta.contract_version != "analytics-contract-v1":
                return ReadinessResponse(status="NOT_READY", reason="CONTRACT_VERSION_MISMATCH")

            if meta.target != "target_next_day_plays":
                return ReadinessResponse(status="NOT_READY", reason="TARGET_MISMATCH")

            return ReadinessResponse(
                status="READY",
                model=self.model_name,
                model_version=version,
            )
        except (ModelCompatibilityError, ModelIncompatibleError) as exc:
            if "schema" in str(exc).lower():
                return ReadinessResponse(status="NOT_READY", reason="FEATURE_SCHEMA_MISMATCH")
            return ReadinessResponse(status="NOT_READY", reason="MODEL_INCOMPATIBLE")
        except ModelArtifactIntegrityError:
            return ReadinessResponse(status="NOT_READY", reason="MODEL_INTEGRITY_FAILURE")
        except ModelNotFoundError:
            return ReadinessResponse(status="NOT_READY", reason="MODEL_UNAVAILABLE")
        except Exception:
            return ReadinessResponse(status="NOT_READY", reason="MODEL_UNAVAILABLE")

    def get_model_status(self) -> ModelStatusResponse:
        """Return operational model status metadata without sensitive paths or credentials."""
        active_entry = self.registry.get_active_model(self.model_name)
        if not active_entry:
            raise ModelNotReadyError("No active model registered.")

        val_metrics = active_entry.get("validation_metrics", {}) or {}
        return ModelStatusResponse(
            model_name=active_entry["model_name"],
            model_version=active_entry["model_version"],
            algorithm=active_entry["algorithm"],
            target=active_entry["target"],
            feature_schema_version=active_entry["feature_schema_version"],
            contract_version=active_entry["contract_version"],
            status=active_entry["status"],
            validation_mae=val_metrics.get("mae"),
            validation_rmse=val_metrics.get("rmse"),
            validation_r2=val_metrics.get("r2"),
        )

    def predict(self, request: PredictionRequest, request_id: str) -> PredictionResponse:
        """Execute batch prediction with bounded execution and metrics tracking."""
        start_time = time.perf_counter()
        batch_size = len(request.records)

        readiness = self.get_readiness()
        if readiness.status != "READY":
            metrics_tracker.record_prediction(success=False, latency_ms=0.0, batch_size=batch_size)
            raise ModelNotReadyError(readiness.reason or "MODEL_UNAVAILABLE")

        try:
            records_data = [rec.model_dump() for rec in request.records]
            input_df = pd.DataFrame(records_data)

            if self._is_baseline_mode:
                preds = input_df["plays"].fillna(0.0).values
                algorithm_name = "naive_previous_day_plays"
            else:
                bundle = self._cached_bundle
                if bundle is None:
                    bundle, _ = self._load_active_model()
                if bundle is None:
                    raise ModelNotReadyError("MODEL_UNAVAILABLE")

                for col in DEFAULT_NUMERIC_FEATURES:
                    if col not in input_df.columns:
                        input_df[col] = 0.0
                    else:
                        input_df[col] = input_df[col].fillna(0.0)

                for col in DEFAULT_CATEGORICAL_FEATURES:
                    if col not in input_df.columns:
                        input_df[col] = 1 if col != "platform" else "IOS"
                    else:
                        input_df[col] = input_df[col].fillna(1 if col != "platform" else "IOS")

                # Perform transform only (never fit)
                X_proc = bundle.preprocessor.transform(input_df)
                raw_preds = bundle.model.predict(X_proc)

                # Validate outputs for NaN or Infinity
                for val in raw_preds:
                    if math.isnan(val) or math.isinf(val):
                        raise ModelOutputInvalidError("Model produced invalid non-finite output (NaN or Infinity).")

                preds = np.clip(raw_preds, 0.0, None)
                algorithm_name = bundle.algorithm

            results: List[PredictionResultItem] = []
            for idx, rec in enumerate(request.records):
                results.append(
                    PredictionResultItem(
                        content_id=rec.content_id,
                        date=rec.date,
                        platform=rec.platform,
                        predicted_next_day_plays=round(float(preds[idx]), 4),
                    )
                )

            elapsed_ms = (time.perf_counter() - start_time) * 1000.0
            metrics_tracker.record_prediction(success=True, latency_ms=elapsed_ms, batch_size=batch_size)

            active_meta = self._cached_metadata or self.registry.get_active_model(self.model_name)
            version_str = active_meta.model_version if isinstance(active_meta, ModelRegistryMetadata) else active_meta["model_version"]

            return PredictionResponse(
                success=True,
                model_name=self.model_name,
                model_version=version_str,
                algorithm=algorithm_name,
                target="target_next_day_plays",
                prediction_count=len(results),
                predictions=results,
                request_id=request_id,
            )
        except (ModelNotReadyError, ModelOutputInvalidError):
            metrics_tracker.record_prediction(success=False, latency_ms=0.0, batch_size=batch_size)
            raise
        except Exception as exc:
            elapsed_ms = (time.perf_counter() - start_time) * 1000.0
            metrics_tracker.record_prediction(success=False, latency_ms=elapsed_ms, batch_size=batch_size)
            raise RuntimeError(f"Prediction execution failed: {str(exc)}") from exc
