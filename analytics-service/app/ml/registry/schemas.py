"""Schemas, metadata contracts, and data structures for Model Registry."""

from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional


@dataclass
class ChecksumManifest:
    """SHA-256 checksum manifest for model package files."""

    algorithm: str = "SHA-256"
    files: Dict[str, str] = field(default_factory=dict)


@dataclass
class ModelMetadata:
    """Governance and provenance metadata for a versioned model package."""

    model_name: str
    model_version: str
    algorithm: str
    target: str
    feature_schema_version: str
    contract_version: str
    created_at: str
    training_data_source: str
    training_date_range: Optional[Dict[str, Optional[str]]] = None
    validation_date_range: Optional[Dict[str, Optional[str]]] = None
    test_date_range: Optional[Dict[str, Optional[str]]] = None
    feature_count: int = 0
    artifact_files: List[str] = field(default_factory=list)


@dataclass
class ModelBundle:
    """Complete executable model artifact bundle."""

    model: Any
    preprocessor: Any
    model_name: str
    model_version: str
    algorithm: str
    target: str
    feature_schema_version: str
    contract_version: str
    feature_names: List[str]
    numeric_features: List[str]
    categorical_features: List[str]

    def predict(self, X: Any) -> Any:
        """Transform input features using preprocessor fit on train and generate predictions."""
        from app.ml.registry.exceptions import ModelCompatibilityError

        if self.model is None or self.preprocessor is None:
            raise ModelCompatibilityError("Model bundle does not contain executable trained model and preprocessor.")

        X_proc = self.preprocessor.transform(X)
        return self.model.predict(X_proc)


@dataclass
class ModelRegistryMetadata:
    """Registry metadata entry for a registered model version."""

    model_name: str
    model_version: str
    algorithm: str
    target: str
    feature_schema_version: str
    contract_version: str
    created_at: str
    training_data_source: str
    training_row_count: int
    validation_row_count: int
    test_row_count: int
    selection_metric: str
    validation_metrics: Dict[str, Any]
    test_metrics: Dict[str, Any]
    baseline_metrics: Dict[str, Any]
    status: str  # "PROVISIONAL", "ACTIVE", "ACTIVE_BASELINE", "DEPRECATED", "ARCHIVED", "MODEL_INVALID"
    artifact_path: Optional[str]
    artifact_sha256: Optional[str]
    model_card_path: Optional[str]
    model_card_sha256: Optional[str]


@dataclass
class RegistryManifest:
    """Registry manifest listing all registered model versions."""

    models: List[Dict[str, Any]] = field(default_factory=list)
