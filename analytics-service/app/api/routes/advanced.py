"""FastAPI routes for Phase 7.4 Advanced Statistical and Business Analytics."""

import logging
from datetime import date
from typing import List, Optional

from fastapi import APIRouter, Depends, HTTPException, Query, Security, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.clients.errors import (
    AnalyticsAuthenticationError,
    AnalyticsAuthorizationError,
    AnalyticsClientError,
    AnalyticsContractError,
    AnalyticsServerError,
    AnalyticsTimeoutError,
)
from app.core.config import Settings, get_settings
from app.processing.errors import DataProcessingError, SensitiveDataError
from app.schemas.advanced import (
    AnomalyDetectionResponse,
    CategoryComparisonItem,
    ContentPerformanceScoreItem,
    DailyTrendItem,
    DistributionAnalysisResponse,
    EngagementAnalyticsResponse,
    GrowthAnalysisResponse,
    InsightsResponse,
    LanguageComparisonItem,
    PlatformComparisonItem,
)
from app.schemas.contract import PlatformEnum
from app.services.analytics_advanced_service import AnalyticsAdvancedService

logger = logging.getLogger("communityott.analytics.api.advanced")

router = APIRouter(prefix="/analytics/advanced", tags=["advanced-analytics"])
security = HTTPBearer(auto_error=False)


def get_advanced_service(settings: Settings = Depends(get_settings)) -> AnalyticsAdvancedService:
    """Dependency injector for AnalyticsAdvancedService."""
    return AnalyticsAdvancedService(settings=settings)


def resolve_auth_token(
    credentials: Optional[HTTPAuthorizationCredentials] = Security(security),
    settings: Settings = Depends(get_settings),
) -> Optional[str]:
    """Resolve Bearer token from Authorization header or development settings."""
    if credentials and credentials.credentials:
        return credentials.credentials
    return settings.DEV_AUTH_TOKEN


def validate_date_range(from_date: Optional[date], to_date: Optional[date]) -> None:
    """Ensure from_date is not strictly after to_date."""
    if from_date and to_date and from_date > to_date:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Invalid date range: 'from' ({from_date}) cannot be after 'to' ({to_date})",
        )


def handle_service_exceptions(exc: Exception) -> None:
    """Translate service and client exceptions into structured HTTP error responses."""
    if isinstance(exc, HTTPException):
        raise exc
    if isinstance(exc, SensitiveDataError):
        logger.error(f"Sensitive PII data detected and rejected: {exc}")
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="Contract payload contains forbidden sensitive fields (PII).",
        )
    if isinstance(exc, DataProcessingError):
        logger.error(f"Data processing error: {exc}")
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=str(exc),
        )
    if isinstance(exc, AnalyticsContractError):
        logger.error(f"Contract schema validation failure: {exc}")
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=f"Data Contract validation error: {str(exc)}",
        )
    if isinstance(exc, AnalyticsAuthenticationError):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authentication required or token expired on Spring Boot export endpoint.",
        )
    if isinstance(exc, AnalyticsAuthorizationError):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Insufficient permissions for analytics export.",
        )
    if isinstance(exc, (AnalyticsTimeoutError, AnalyticsServerError)):
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Upstream Spring Boot service unavailable or timed out.",
        )
    if isinstance(exc, AnalyticsClientError):
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=f"Analytics client communication failure: {str(exc)}",
        )
    logger.exception(f"Unexpected error in advanced analytics API: {exc}")
    raise HTTPException(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        detail="Internal error during advanced analytics calculation.",
    )


@router.get(
    "/engagement",
    response_model=EngagementAnalyticsResponse,
    summary="Viewer engagement and streaming quality analytics",
)
async def get_engagement_analytics(
    from_date: Optional[date] = Query(None, alias="from", description="Start date (YYYY-MM-DD)"),
    to_date: Optional[date] = Query(None, alias="to", description="End date (YYYY-MM-DD)"),
    platform: Optional[PlatformEnum] = Query(None, description="Filter by platform"),
    content_id: Optional[int] = Query(None, ge=1, description="Filter by content ID"),
    auth_token: Optional[str] = Depends(resolve_auth_token),
    service: AnalyticsAdvancedService = Depends(get_advanced_service),
) -> EngagementAnalyticsResponse:
    """Return holistic viewer engagement rates, completion ratios, and streaming quality metrics."""
    validate_date_range(from_date, to_date)
    try:
        return await service.get_engagement_analytics(
            from_date=from_date,
            to_date=to_date,
            platform=platform,
            content_id=content_id,
            auth_token=auth_token,
        )
    except Exception as exc:
        handle_service_exceptions(exc)


@router.get(
    "/content",
    response_model=List[ContentPerformanceScoreItem],
    summary="Content performance scoring and rankings",
)
async def get_content_performance(
    from_date: Optional[date] = Query(None, alias="from", description="Start date (YYYY-MM-DD)"),
    to_date: Optional[date] = Query(None, alias="to", description="End date (YYYY-MM-DD)"),
    platform: Optional[PlatformEnum] = Query(None, description="Filter by platform"),
    by: str = Query("performance_score", description="Sort metric (performance_score, plays, watch_time, unique_viewers, completion_rate)"),
    limit: int = Query(10, ge=1, le=100, description="Top N results (1-100)"),
    auth_token: Optional[str] = Depends(resolve_auth_token),
    service: AnalyticsAdvancedService = Depends(get_advanced_service),
) -> List[ContentPerformanceScoreItem]:
    """Rank top content with min-max normalized performance scores and deterministic tie-breaking."""
    validate_date_range(from_date, to_date)
    try:
        return await service.get_content_performance(
            from_date=from_date,
            to_date=to_date,
            platform=platform,
            by=by,
            limit=limit,
            auth_token=auth_token,
        )
    except Exception as exc:
        handle_service_exceptions(exc)


@router.get(
    "/growth",
    response_model=GrowthAnalysisResponse,
    summary="Period-over-period growth analysis",
)
async def get_growth_analysis(
    from_date: Optional[date] = Query(None, alias="from", description="Start date (YYYY-MM-DD)"),
    to_date: Optional[date] = Query(None, alias="to", description="End date (YYYY-MM-DD)"),
    platform: Optional[PlatformEnum] = Query(None, description="Filter by platform"),
    split_date: Optional[date] = Query(None, description="Explicit partition date for current vs previous period"),
    auth_token: Optional[str] = Depends(resolve_auth_token),
    service: AnalyticsAdvancedService = Depends(get_advanced_service),
) -> GrowthAnalysisResponse:
    """Compute period-over-period growth metrics, percentage changes, and directional trends."""
    validate_date_range(from_date, to_date)
    try:
        return await service.get_growth_analysis(
            from_date=from_date,
            to_date=to_date,
            platform=platform,
            split_date=split_date,
            auth_token=auth_token,
        )
    except Exception as exc:
        handle_service_exceptions(exc)


@router.get(
    "/trends",
    response_model=List[DailyTrendItem],
    summary="Daily time-series trends and day-over-day growth",
)
async def get_daily_trends(
    from_date: Optional[date] = Query(None, alias="from", description="Start date (YYYY-MM-DD)"),
    to_date: Optional[date] = Query(None, alias="to", description="End date (YYYY-MM-DD)"),
    platform: Optional[PlatformEnum] = Query(None, description="Filter by platform"),
    content_id: Optional[int] = Query(None, ge=1, description="Filter by content ID"),
    auth_token: Optional[str] = Depends(resolve_auth_token),
    service: AnalyticsAdvancedService = Depends(get_advanced_service),
) -> List[DailyTrendItem]:
    """Generate daily time-series metrics with day-over-day growth rates sorted chronologically."""
    validate_date_range(from_date, to_date)
    try:
        return await service.get_daily_trends(
            from_date=from_date,
            to_date=to_date,
            platform=platform,
            content_id=content_id,
            auth_token=auth_token,
        )
    except Exception as exc:
        handle_service_exceptions(exc)


@router.get(
    "/platforms",
    response_model=List[PlatformComparisonItem],
    summary="Platform comparison and viewing market shares",
)
async def get_platform_comparison(
    from_date: Optional[date] = Query(None, alias="from", description="Start date (YYYY-MM-DD)"),
    to_date: Optional[date] = Query(None, alias="to", description="End date (YYYY-MM-DD)"),
    content_id: Optional[int] = Query(None, ge=1, description="Filter by content ID"),
    auth_token: Optional[str] = Depends(resolve_auth_token),
    service: AnalyticsAdvancedService = Depends(get_advanced_service),
) -> List[PlatformComparisonItem]:
    """Compare viewing and playback reliability across IOS, ANDROID, and WEB platforms."""
    validate_date_range(from_date, to_date)
    try:
        return await service.get_platform_comparison(
            from_date=from_date,
            to_date=to_date,
            content_id=content_id,
            auth_token=auth_token,
        )
    except Exception as exc:
        handle_service_exceptions(exc)


@router.get(
    "/categories",
    response_model=List[CategoryComparisonItem],
    summary="Category comparison and catalog distribution shares",
)
async def get_category_comparison(
    from_date: Optional[date] = Query(None, alias="from", description="Start date (YYYY-MM-DD)"),
    to_date: Optional[date] = Query(None, alias="to", description="End date (YYYY-MM-DD)"),
    platform: Optional[PlatformEnum] = Query(None, description="Filter by platform"),
    auth_token: Optional[str] = Depends(resolve_auth_token),
    service: AnalyticsAdvancedService = Depends(get_advanced_service),
) -> List[CategoryComparisonItem]:
    """Compare viewing metrics and total volume shares across content categories."""
    validate_date_range(from_date, to_date)
    try:
        return await service.get_category_comparison(
            from_date=from_date,
            to_date=to_date,
            platform=platform,
            auth_token=auth_token,
        )
    except Exception as exc:
        handle_service_exceptions(exc)


@router.get(
    "/languages",
    response_model=List[LanguageComparisonItem],
    summary="Language comparison and catalog distribution shares",
)
async def get_language_comparison(
    from_date: Optional[date] = Query(None, alias="from", description="Start date (YYYY-MM-DD)"),
    to_date: Optional[date] = Query(None, alias="to", description="End date (YYYY-MM-DD)"),
    platform: Optional[PlatformEnum] = Query(None, description="Filter by platform"),
    auth_token: Optional[str] = Depends(resolve_auth_token),
    service: AnalyticsAdvancedService = Depends(get_advanced_service),
) -> List[LanguageComparisonItem]:
    """Compare viewing metrics and total volume shares across content languages."""
    validate_date_range(from_date, to_date)
    try:
        return await service.get_language_comparison(
            from_date=from_date,
            to_date=to_date,
            platform=platform,
            auth_token=auth_token,
        )
    except Exception as exc:
        handle_service_exceptions(exc)


@router.get(
    "/distributions",
    response_model=DistributionAnalysisResponse,
    summary="Statistical distributions and percentiles (P25 to P95)",
)
async def get_distribution_analysis(
    from_date: Optional[date] = Query(None, alias="from", description="Start date (YYYY-MM-DD)"),
    to_date: Optional[date] = Query(None, alias="to", description="End date (YYYY-MM-DD)"),
    platform: Optional[PlatformEnum] = Query(None, description="Filter by platform"),
    auth_token: Optional[str] = Depends(resolve_auth_token),
    service: AnalyticsAdvancedService = Depends(get_advanced_service),
) -> DistributionAnalysisResponse:
    """Compute sample distributions, mean, median, sample std (ddof=1), and percentiles (P25-P95)."""
    validate_date_range(from_date, to_date)
    try:
        return await service.get_distribution_analysis(
            from_date=from_date,
            to_date=to_date,
            platform=platform,
            auth_token=auth_token,
        )
    except Exception as exc:
        handle_service_exceptions(exc)


@router.get(
    "/anomalies",
    response_model=AnomalyDetectionResponse,
    summary="Statistical anomaly detection using IQR",
)
async def get_anomalies(
    from_date: Optional[date] = Query(None, alias="from", description="Start date (YYYY-MM-DD)"),
    to_date: Optional[date] = Query(None, alias="to", description="End date (YYYY-MM-DD)"),
    platform: Optional[PlatformEnum] = Query(None, description="Filter by platform"),
    auth_token: Optional[str] = Depends(resolve_auth_token),
    service: AnalyticsAdvancedService = Depends(get_advanced_service),
) -> AnomalyDetectionResponse:
    """Detect statistical outliers using the Interquartile Range (IQR) method."""
    validate_date_range(from_date, to_date)
    try:
        return await service.get_anomalies(
            from_date=from_date,
            to_date=to_date,
            platform=platform,
            auth_token=auth_token,
        )
    except Exception as exc:
        handle_service_exceptions(exc)


@router.get(
    "/insights",
    response_model=InsightsResponse,
    summary="Actionable business and platform health insights",
)
async def get_insights(
    from_date: Optional[date] = Query(None, alias="from", description="Start date (YYYY-MM-DD)"),
    to_date: Optional[date] = Query(None, alias="to", description="End date (YYYY-MM-DD)"),
    platform: Optional[PlatformEnum] = Query(None, description="Filter by platform"),
    auth_token: Optional[str] = Depends(resolve_auth_token),
    service: AnalyticsAdvancedService = Depends(get_advanced_service),
) -> InsightsResponse:
    """Evaluate deterministic business health heuristics across content and platform."""
    validate_date_range(from_date, to_date)
    try:
        return await service.get_insights(
            from_date=from_date,
            to_date=to_date,
            platform=platform,
            auth_token=auth_token,
        )
    except Exception as exc:
        handle_service_exceptions(exc)
