"""CommunityOTT Machine Learning Foundation package (Phase 7.5)."""

from app.ml.datasets.dataset_builder import MLDeliveryBundle, MLDatasetBuilder
from app.ml.features.feature_builder import (
    BASE_FEATURE_COLUMNS,
    MLFeatureError,
    TARGET_COLUMNS,
    build_ml_features,
    validate_features,
)
from app.ml.metadata.feature_metadata import FEATURE_REGISTRY, get_feature_registry
from app.ml.preprocessing.pipeline import (
    DEFAULT_CATEGORICAL_FEATURES,
    DEFAULT_NUMERIC_FEATURES,
    create_preprocessing_pipeline,
    fit_and_transform_splits,
)
from app.ml.schemas.feature_schema import (
    FEATURE_SCHEMA_VERSION,
    FeatureMetadataItem,
    FeatureMetadataResponse,
)

__all__ = [
    "FEATURE_SCHEMA_VERSION",
    "BASE_FEATURE_COLUMNS",
    "TARGET_COLUMNS",
    "MLFeatureError",
    "FeatureMetadataItem",
    "FeatureMetadataResponse",
    "FEATURE_REGISTRY",
    "get_feature_registry",
    "build_ml_features",
    "validate_features",
    "create_preprocessing_pipeline",
    "fit_and_transform_splits",
    "DEFAULT_NUMERIC_FEATURES",
    "DEFAULT_CATEGORICAL_FEATURES",
    "MLDatasetBuilder",
    "MLDeliveryBundle",
]
