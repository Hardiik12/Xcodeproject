"""Custom exceptions for Model Registry, artifact integrity, and compatibility."""


class ModelRegistryError(Exception):
    """Base exception for all model registry operations."""

    pass


class ActiveModelUnavailableError(ModelRegistryError):
    """Raised when no active validated model is available in the registry."""

    pass


class ModelInvalidError(ModelRegistryError):
    """Raised when a model artifact fails deserialization or integrity checks."""

    pass


class ModelIncompatibleError(ModelRegistryError):
    """Raised when model metadata or schema is incompatible with current service contract."""

    pass


class ModelNotFoundError(ModelRegistryError):
    """Raised when a requested model name or version is not found in the registry."""

    pass


class ModelChecksumError(ModelRegistryError):
    """Raised when SHA-256 checksum validation fails."""

    pass


class ModelArtifactIntegrityError(ModelChecksumError):
    """Raised when artifact integrity validation fails."""

    pass


class ModelVersionAlreadyExistsError(ModelRegistryError):
    """Raised when attempting to register an existing version without overwrite=True."""

    pass


class ModelCompatibilityError(ModelIncompatibleError):
    """Raised when feature schema, contract, or target is incompatible."""

    pass
