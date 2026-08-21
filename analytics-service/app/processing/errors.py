"""Processing exceptions for Analytics Data Processing layer (Phase 7.3)."""


class DataProcessingError(Exception):
    """Base exception for all processing layer errors."""
    pass


class DataValidationError(DataProcessingError):
    """Raised when an analytics DataFrame violates schema, invariants, or bounds."""
    pass


class SensitiveDataError(DataValidationError):
    """Raised when an incoming dataset contains forbidden PII or sensitive columns."""
    pass


class EmptyDatasetError(DataProcessingError):
    """Raised when an operation requires data but an empty dataset is provided."""
    pass
