"""Analytics processing module exposing DataFrame builders, validators, aggregations, and statistics."""

from app.processing.errors import (
    DataProcessingError,
    DataValidationError,
    SensitiveDataError,
    EmptyDatasetError,
)
from app.processing.dataframe_builder import build_dataframe
from app.processing.data_cleaner import clean_dataframe
from app.processing.data_validator import validate_dataframe
from app.processing.aggregations import (
    aggregate_by_content,
    aggregate_by_category,
    aggregate_by_language,
    aggregate_by_platform,
    aggregate_by_date,
)
from app.processing.statistics import (
    calculate_metric_statistics,
    get_top_content,
)

__all__ = [
    "DataProcessingError",
    "DataValidationError",
    "SensitiveDataError",
    "EmptyDatasetError",
    "build_dataframe",
    "clean_dataframe",
    "validate_dataframe",
    "aggregate_by_content",
    "aggregate_by_category",
    "aggregate_by_language",
    "aggregate_by_platform",
    "aggregate_by_date",
    "calculate_metric_statistics",
    "get_top_content",
]
