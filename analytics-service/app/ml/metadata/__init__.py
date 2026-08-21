"""ML metadata package."""

from app.ml.metadata.feature_metadata import FEATURE_REGISTRY, get_feature_registry

__all__ = [
    "FEATURE_REGISTRY",
    "get_feature_registry",
]
