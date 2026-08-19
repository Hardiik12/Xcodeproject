"""Health check endpoint."""

from fastapi import APIRouter, Depends
from app.core.config import Settings, get_settings
from app.schemas.metadata import HealthResponse

router = APIRouter(prefix="/analytics", tags=["Health & Status"])


@router.get(
    "/health",
    response_model=HealthResponse,
    summary="Service Health Check",
    description="Returns the operational status of the CommunityOTT Python Analytics Service.",
)
async def get_health(settings: Settings = Depends(get_settings)) -> HealthResponse:
    """Return health status without requiring external database dependencies."""
    return HealthResponse(
        success=True,
        service=settings.APP_NAME,
        status="UP",
        version=settings.APP_VERSION,
    )
