"""ML Serving subpackage for prediction endpoints, health, readiness, and metrics."""

from app.ml.serving.metrics import PredictionMetricsTracker, metrics_tracker
from app.ml.serving.prediction_service import ModelNotReadyError, PredictionService
from app.ml.serving.schemas import (
    ModelStatusResponse,
    PredictionInputRecord,
    PredictionRequest,
    PredictionResponse,
    PredictionResultItem,
    ReadinessResponse,
    StructuredErrorDetail,
    StructuredErrorResponse,
)

__all__ = [
    "PredictionService",
    "ModelNotReadyError",
    "PredictionMetricsTracker",
    "metrics_tracker",
    "PredictionInputRecord",
    "PredictionRequest",
    "PredictionResponse",
    "PredictionResultItem",
    "StructuredErrorDetail",
    "StructuredErrorResponse",
    "ReadinessResponse",
    "ModelStatusResponse",
]
