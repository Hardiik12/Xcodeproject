"""Model Registry subpackage for versioned persistence, checksums, and artifact integrity."""

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
from app.ml.registry.model_registry import ModelRegistry, calculate_artifact_sha256
from app.ml.registry.schemas import (
    ChecksumManifest,
    ModelBundle,
    ModelMetadata,
    ModelRegistryMetadata,
    RegistryManifest,
)

__all__ = [
    "ModelRegistry",
    "calculate_artifact_sha256",
    "ModelRegistryError",
    "ActiveModelUnavailableError",
    "ModelInvalidError",
    "ModelIncompatibleError",
    "ModelNotFoundError",
    "ModelChecksumError",
    "ModelArtifactIntegrityError",
    "ModelVersionAlreadyExistsError",
    "ModelCompatibilityError",
    "ChecksumManifest",
    "ModelMetadata",
    "ModelBundle",
    "ModelRegistryMetadata",
    "RegistryManifest",
]
