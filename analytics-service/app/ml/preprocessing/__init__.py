"""ML preprocessing package."""

from app.ml.preprocessing.pipeline import (
    DEFAULT_CATEGORICAL_FEATURES,
    DEFAULT_NUMERIC_FEATURES,
    create_preprocessing_pipeline,
    fit_and_transform_splits,
)

__all__ = [
    "DEFAULT_NUMERIC_FEATURES",
    "DEFAULT_CATEGORICAL_FEATURES",
    "create_preprocessing_pipeline",
    "fit_and_transform_splits",
]
