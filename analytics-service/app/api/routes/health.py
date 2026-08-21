"""Health and readiness probe endpoints."""

from fastapi import APIRouter, Depends, Response, status
from app.core.config import Settings, get_settings
from app.ml.serving.prediction_service import PredictionService
from app.ml.serving.schemas import ReadinessResponse
from app.schemas.metadata import HealthResponse

router = APIRouter(prefix="/analytics", tags=["Health & Status"])

# Global prediction service instance for dependency injection
_prediction_service = PredictionService()
_prediction_service.initialize()


def get_prediction_service() -> PredictionService:
    """Dependency provider for PredictionService."""
    return _prediction_service


@router.get(
    "/health",
    response_model=HealthResponse,
    status_code=status.HTTP_200_OK,
    summary="Liveness Health Check Probe",
    description="Returns HTTP 200 UP if the FastAPI process is alive. Does NOT fail if ML model is unavailable.",
)
async def get_health(settings: Settings = Depends(get_settings)) -> HealthResponse:
    """Return liveness status."""
    return HealthResponse(
        success=True,
        service=settings.APP_NAME,
        status="UP",
        version=settings.APP_VERSION,
    )


@router.get(
    "/ready",
    response_model=ReadinessResponse,
    summary="Readiness Health Check Probe",
    description=(
        "Returns HTTP 200 READY if active ML model/baseline is available and verified. "
        "Returns HTTP 503 NOT_READY if model is unavailable or corrupted."
    ),
    responses={
        200: {"description": "Service is ready to serve predictions"},
        503: {"description": "Service is not ready to serve predictions"},
    },
)
async def get_readiness(
    response: Response,
    service: PredictionService = Depends(get_prediction_service),
) -> ReadinessResponse:
    """Return readiness status without exposing file paths or secrets."""
    readiness = service.get_readiness()
    if readiness.status != "READY":
        response.status_code = status.HTTP_503_SERVICE_UNAVAILABLE
    else:
        response.status_code = status.HTTP_200_OK
    return readiness
