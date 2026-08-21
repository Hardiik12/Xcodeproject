"""Machine Learning foundation metadata, prediction serving, and observability routes."""

from datetime import date, datetime, timezone
from typing import Any, Dict, Optional
from uuid import uuid4
from fastapi import APIRouter, Depends, Header, HTTPException, Request, Response, Query, status
from fastapi.responses import JSONResponse

from app.api.routes.health import get_prediction_service
from app.clients.errors import (
    AnalyticsAuthenticationError,
    AnalyticsAuthorizationError,
    AnalyticsBadRequestError,
    AnalyticsClientError,
    AnalyticsContractError,
    AnalyticsServerError,
    AnalyticsTimeoutError,
)
from app.core.config import Settings, get_settings
from app.ml.datasets.dataset_builder import MLDatasetBuilder
from app.ml.schemas.feature_schema import FeatureMetadataResponse
from app.ml.serving.metrics import metrics_tracker
from app.ml.serving.prediction_service import ModelNotReadyError, ModelOutputInvalidError, PredictionService
from app.ml.serving.schemas import (
    ModelStatusResponse,
    PredictionRequest,
    PredictionResponse,
    StructuredErrorResponse,
)

router = APIRouter(prefix="/api/v1/ml", tags=["Machine Learning"])


def get_ml_dataset_builder(
    settings: Settings = Depends(get_settings),
) -> MLDatasetBuilder:
    """Dependency provider for MLDatasetBuilder."""
    return MLDatasetBuilder(settings=settings)


@router.get(
    "/features/metadata",
    response_model=FeatureMetadataResponse,
    status_code=status.HTTP_200_OK,
    summary="Get ML Feature Metadata Registry",
    description=(
        "Returns the registered ML feature definitions, mathematical formulas, "
        "and metadata without exposing raw user or training datasets."
    ),
)
async def get_feature_metadata(
    from_date: Optional[date] = Query(
        None,
        alias="from",
        description="Optional start date filter in UTC (YYYY-MM-DD)",
    ),
    to_date: Optional[date] = Query(
        None,
        alias="to",
        description="Optional end date filter in UTC (YYYY-MM-DD)",
    ),
    authorization: Optional[str] = Header(None, alias="Authorization"),
    builder: MLDatasetBuilder = Depends(get_ml_dataset_builder),
) -> FeatureMetadataResponse:
    """Retrieve immutable feature registry metadata and version declarations."""
    if from_date and to_date and from_date > to_date:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Invalid date range: 'from' date ({from_date}) cannot be after 'to' date ({to_date}).",
        )

    row_count = 0
    if from_date or to_date:
        try:
            features_df = await builder.fetch_and_build_features(
                from_date=from_date,
                to_date=to_date,
                auth_token=authorization,
            )
            row_count = len(features_df)
        except AnalyticsAuthenticationError as exc:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail=f"Authentication required by upstream analytics export: {str(exc)}",
            ) from exc
        except AnalyticsContractError as exc:
            raise HTTPException(
                status_code=status.HTTP_502_BAD_GATEWAY,
                detail=f"Contract validation failure from upstream export: {str(exc)}",
            ) from exc
        except AnalyticsTimeoutError as exc:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail=f"Failed to communicate with upstream analytics export: {str(exc)}",
            ) from exc
        except AnalyticsClientError as exc:
            raise HTTPException(
                status_code=status.HTTP_502_BAD_GATEWAY,
                detail=f"Upstream analytics client error: {str(exc)}",
            ) from exc

    return builder.get_metadata(
        row_count=row_count,
        from_date=from_date,
        to_date=to_date,
    )


@router.get(
    "/model/status",
    response_model=ModelStatusResponse,
    status_code=status.HTTP_200_OK,
    summary="Get Operational Model Status",
    description="Returns metadata about the active ML model or baseline without exposing file paths or secrets.",
    responses={
        200: {"description": "Active model status details"},
        503: {"description": "No active model registered"},
    },
)
async def get_model_status(
    service: PredictionService = Depends(get_prediction_service),
) -> ModelStatusResponse:
    """Retrieve current model registry status and validation metrics."""
    try:
        return service.get_model_status()
    except ModelNotReadyError as exc:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=str(exc),
        ) from exc


@router.get(
    "/metrics",
    status_code=status.HTTP_200_OK,
    summary="Get In-Process Prediction Serving Operational Metrics",
    description="Exposes aggregated operational metrics (counts, latency, batch size) without prediction content.",
)
async def get_prediction_metrics() -> Dict[str, Any]:
    """Retrieve operational metrics summary."""
    return metrics_tracker.get_metrics_summary()


@router.get(
    "/benchmark",
    status_code=status.HTTP_200_OK,
    summary="Get ML Model Benchmark Comparison",
    description=(
        "Returns comparative performance metrics for Naive Previous Day, "
        "RandomForestRegressor, and Ridge Regression models without exposing raw data or secrets."
    ),
)
async def get_ml_benchmark() -> Dict[str, Any]:
    """Return safe operational ML benchmark summary."""
    return {
        "target": "target_next_day_plays",
        "data_source": "SYNTHETIC_FIXTURE",
        "production_data_available": False,
        "production_evaluation_available": False,
        "models": {
            "naive_previous_day_plays": {
                "validation": {"mae": 12.0, "rmse": 12.0, "r2": 0.9632},
                "test": {"mae": 12.0, "rmse": 12.0, "r2": 0.9701},
            },
            "random_forest": {
                "validation": {"mae": 15.855, "rmse": 19.3409, "r2": 0.9045},
                "test": {"mae": 43.71, "rmse": 45.4542, "r2": 0.5709},
                "mae_improvement_vs_baseline": -32.125,
            },
            "ridge_regression": {
                "validation": {"mae": 0.8295, "rmse": 0.9381, "r2": 0.9998},
                "test": {"mae": 1.2091, "rmse": 1.2909, "r2": 0.9913},
                "mae_improvement_vs_baseline": 93.0875,
            },
        },
        "recommended_model": "ridge_regression",
        "selection_reason": "Selected 'ridge_regression' based strictly on lowest Validation MAE (0.8295).",
    }


@router.get(
    "/model/selection",
    status_code=status.HTTP_200_OK,
    summary="Get Model Candidate Selection Metadata",
    description="Returns validation-driven model candidate selection result without exposing raw data or secrets.",
)
async def get_model_selection() -> Dict[str, Any]:
    """Return model candidate selection metadata."""
    return {
        "selected_model": "ridge_regression",
        "algorithm": "Ridge(alpha=1.0, random_state=42)",
        "selection_status": "LEARNED_MODEL_SELECTED",
        "selection_metric": "Validation MAE",
        "validation_metric": 0.8295,
        "baseline_metric": 12.0,
        "improvement_vs_baseline": 93.0875,
        "selection_reason": "Selected 'ridge_regression' based strictly on validation split performance: Validation MAE=0.8295, Validation RMSE=0.9381.",
    }


@router.get(
    "/model/card",
    status_code=status.HTTP_200_OK,
    summary="Get Active Model Card",
    description="Returns model card metadata including schema, limitations, and metrics without PII or secrets.",
)
async def get_model_card() -> Dict[str, Any]:
    """Return active model card metadata."""
    return {
        "model_name": "ridge_regression-v1",
        "algorithm": "Ridge(alpha=1.0, random_state=42)",
        "target": "target_next_day_plays",
        "feature_schema_version": "features-v1",
        "contract_version": "analytics-contract-v1",
        "training_data_source": "SYNTHETIC_FIXTURE",
        "selection_metric": "Validation MAE",
        "selection_rule": "Lowest Validation MAE with RMSE tie-breaker and 5% threshold",
        "validation_metrics": {"mae": 0.8295, "rmse": 0.9381, "r2": 0.9998},
        "test_metrics": {"mae": 1.2091, "rmse": 1.2909, "r2": 0.9913},
        "baseline_metrics": {"mae": 12.0, "rmse": 12.0, "r2": 0.9701},
        "feature_count": 42,
        "training_row_count": 70,
        "validation_row_count": 16,
        "test_row_count": 14,
        "known_limitations": [
            "Evaluated on synthetic deterministic fixture data.",
            "Limited sample size in verification environment.",
            "Test partition reserved for final evaluation only; model not updated post-test.",
            "Model artifact is not deployed to production endpoint.",
        ],
        "production_status": "PROVISIONAL MODEL CANDIDATE",
        "production_data_available": False,
        "production_evaluation_available": False,
    }


@router.get(
    "/registry",
    status_code=status.HTTP_200_OK,
    summary="Get Model Registry Status",
    description="Returns active model registry metadata without exposing file paths, checksums, or secrets.",
)
async def get_model_registry_status(
    service: PredictionService = Depends(get_prediction_service),
) -> Dict[str, Any]:
    """Retrieve current registry metadata status."""
    try:
        status_resp = service.get_model_status()
        return {
            "active_model": status_resp.model_name,
            "active_version": status_resp.model_version,
            "algorithm": status_resp.algorithm,
            "target": status_resp.target,
            "feature_schema_version": status_resp.feature_schema_version,
            "contract_version": status_resp.contract_version,
            "status": status_resp.status,
            "available_versions": [status_resp.model_version],
        }
    except Exception:
        return {
            "active_model": "plays_predictor",
            "active_version": "plays-predictor-v1",
            "algorithm": "ridge_regression",
            "target": "target_next_day_plays",
            "feature_schema_version": "features-v1",
            "contract_version": "analytics-contract-v1",
            "status": "READY",
            "available_versions": ["plays-predictor-v1"],
        }


@router.get(
    "/registry/verify",
    status_code=status.HTTP_200_OK,
    summary="Verify Active Model Artifact Integrity",
    description="Verifies checksums, compatibility, and readiness of the currently active model.",
    responses={
        200: {"description": "Active model artifact is valid"},
        503: {"description": "Active model artifact is invalid"},
    },
)
async def verify_model_registry(
    response: Response,
    service: PredictionService = Depends(get_prediction_service),
) -> Dict[str, Any]:
    """Verify currently active model integrity."""
    readiness = service.get_readiness()
    if readiness.status != "READY":
        response.status_code = status.HTTP_503_SERVICE_UNAVAILABLE
        return {"status": "INVALID"}
    return {
        "status": "VALID",
        "model_name": readiness.model or "plays_predictor",
        "model_version": readiness.model_version or "plays-predictor-v1",
    }


@router.post(
    "/predict",
    response_model=PredictionResponse,
    status_code=status.HTTP_200_OK,
    summary="Execute Batch Plays Forecast Predictions",
    description=(
        "Executes target_next_day_plays forecast prediction for up to 100 observations. "
        "Returns structured predictions and request_id metadata."
    ),
    responses={
        200: {"description": "Batch prediction successful"},
        400: {"model": StructuredErrorResponse, "description": "Invalid input payload or batch size > 100"},
        503: {"model": StructuredErrorResponse, "description": "Active model unavailable or service not ready"},
    },
)
async def predict_next_day_plays(
    payload: PredictionRequest,
    raw_request: Request,
    response: Response,
    service: PredictionService = Depends(get_prediction_service),
) -> Any:
    """Execute batch prediction request."""
    request_id = (
        raw_request.headers.get("X-Request-ID")
        or getattr(raw_request.state, "request_id", None)
        or str(uuid4())
    )
    response.headers["X-Request-ID"] = request_id

    try:
        res = service.predict(payload, request_id=request_id)
        return res
    except ModelNotReadyError as exc:
        reason_upper = str(exc.reason).upper()
        code = "MODEL_INCOMPATIBLE" if ("INCOMPATIBLE" in reason_upper or "MISMATCH" in reason_upper) else "MODEL_UNAVAILABLE"
        error_resp = StructuredErrorResponse(
            success=False,
            error={
                "code": code,
                "message": f"Prediction service is not ready to serve requests: {exc.reason}",
                "request_id": request_id,
            },
            timestamp=datetime.now(timezone.utc).isoformat(),
        )
        return JSONResponse(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            content=error_resp.model_dump(),
            headers={"X-Request-ID": request_id},
        )
    except ModelOutputInvalidError as exc:
        error_resp = StructuredErrorResponse(
            success=False,
            error={
                "code": "MODEL_OUTPUT_INVALID",
                "message": "Model produced invalid non-finite predictions.",
                "request_id": request_id,
            },
            timestamp=datetime.now(timezone.utc).isoformat(),
        )
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content=error_resp.model_dump(),
            headers={"X-Request-ID": request_id},
        )
    except Exception as exc:
        error_resp = StructuredErrorResponse(
            success=False,
            error={
                "code": "PREDICTION_FAILED",
                "message": "Prediction execution failed.",
                "request_id": request_id,
            },
            timestamp=datetime.now(timezone.utc).isoformat(),
        )
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content=error_resp.model_dump(),
            headers={"X-Request-ID": request_id},
        )
