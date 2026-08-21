"""Internal API endpoints for analytics data processing, aggregations, and statistics."""

from datetime import date
from typing import List, Optional
from fastapi import APIRouter, Depends, Header, HTTPException, Query, status

from app.clients.errors import (
    AnalyticsAuthenticationError,
    AnalyticsAuthorizationError,
    AnalyticsBadRequestError,
    AnalyticsContractError,
    AnalyticsServerError,
    AnalyticsTimeoutError,
)
from app.processing.errors import DataValidationError, SensitiveDataError
from app.schemas.contract import PlatformEnum
from app.schemas.processing import (
    CategoryAggregatedItem,
    ContentPerformanceItem,
    LanguageAggregatedItem,
    PlatformAggregatedItem,
    ProcessingSummaryResponse,
)
from app.services.analytics_processing_service import AnalyticsProcessingService

router = APIRouter(prefix="/analytics/processing", tags=["Analytics Processing"])


def get_processing_service() -> AnalyticsProcessingService:
    """Dependency provider for AnalyticsProcessingService."""
    return AnalyticsProcessingService()


def _handle_processing_exceptions(err: Exception) -> HTTPException:
    """Map processing and upstream client exceptions to standard HTTP error responses."""
    if isinstance(err, SensitiveDataError):
        return HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=f"Privacy violation: {err}")
    if isinstance(err, DataValidationError):
        return HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=f"Data validation failure: {err}")
    if isinstance(err, AnalyticsBadRequestError):
        return HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(err))
    if isinstance(err, AnalyticsAuthenticationError):
        return HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail=str(err))
    if isinstance(err, AnalyticsAuthorizationError):
        return HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(err))
    if isinstance(err, AnalyticsTimeoutError):
        return HTTPException(status_code=status.HTTP_504_GATEWAY_TIMEOUT, detail=str(err))
    if isinstance(err, (AnalyticsContractError, AnalyticsServerError)):
        return HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail=str(err))
    return HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(err))


@router.get(
    "/summary",
    response_model=ProcessingSummaryResponse,
    summary="Compute Analytics Summary & Statistical Distributions",
    description="Fetches analytics-contract-v1 dataset and returns sample statistics for key metrics.",
)
async def get_summary(
    from_date: Optional[date] = Query(None, alias="from", description="Start date (YYYY-MM-DD)"),
    to_date: Optional[date] = Query(None, alias="to", description="End date (YYYY-MM-DD)"),
    platform: Optional[PlatformEnum] = Query(None, description="Platform filter (IOS, ANDROID, WEB)"),
    content_id: Optional[int] = Query(None, description="Content ID filter"),
    category_id: Optional[int] = Query(None, description="Category ID filter"),
    language_id: Optional[int] = Query(None, description="Language ID filter"),
    authorization: Optional[str] = Header(None, description="Authorization Bearer token"),
    service: AnalyticsProcessingService = Depends(get_processing_service),
):
    try:
        return await service.get_summary(
            from_date=from_date,
            to_date=to_date,
            platform=platform,
            content_id=content_id,
            category_id=category_id,
            language_id=language_id,
            auth_token=authorization,
        )
    except Exception as err:
        raise _handle_processing_exceptions(err) from err


@router.get(
    "/content",
    response_model=List[ContentPerformanceItem],
    summary="Rank Top Performing Content",
    description="Aggregates performance by content_id and ranks assets by target metric.",
)
async def get_content_performance(
    from_date: Optional[date] = Query(None, alias="from", description="Start date (YYYY-MM-DD)"),
    to_date: Optional[date] = Query(None, alias="to", description="End date (YYYY-MM-DD)"),
    platform: Optional[PlatformEnum] = Query(None, description="Platform filter (IOS, ANDROID, WEB)"),
    by: str = Query("plays", description="Ranking metric (plays, watch_time_seconds, unique_viewers, completion_rate)"),
    limit: int = Query(10, ge=1, le=100, description="Max assets to return"),
    authorization: Optional[str] = Header(None, description="Authorization Bearer token"),
    service: AnalyticsProcessingService = Depends(get_processing_service),
):
    try:
        return await service.get_content_performance(
            from_date=from_date,
            to_date=to_date,
            platform=platform,
            by=by,
            limit=limit,
            auth_token=authorization,
        )
    except Exception as err:
        raise _handle_processing_exceptions(err) from err


@router.get(
    "/platforms",
    response_model=List[PlatformAggregatedItem],
    summary="Platform Metric Breakdown",
    description="Aggregates and compares playback performance across client platforms (IOS, ANDROID, WEB).",
)
async def get_platform_breakdown(
    from_date: Optional[date] = Query(None, alias="from", description="Start date (YYYY-MM-DD)"),
    to_date: Optional[date] = Query(None, alias="to", description="End date (YYYY-MM-DD)"),
    content_id: Optional[int] = Query(None, description="Content ID filter"),
    authorization: Optional[str] = Header(None, description="Authorization Bearer token"),
    service: AnalyticsProcessingService = Depends(get_processing_service),
):
    try:
        return await service.get_platform_breakdown(
            from_date=from_date,
            to_date=to_date,
            content_id=content_id,
            auth_token=authorization,
        )
    except Exception as err:
        raise _handle_processing_exceptions(err) from err


@router.get(
    "/categories",
    response_model=List[CategoryAggregatedItem],
    summary="Category Metric Breakdown",
    description="Aggregates playback performance grouped across content categories.",
)
async def get_category_breakdown(
    from_date: Optional[date] = Query(None, alias="from", description="Start date (YYYY-MM-DD)"),
    to_date: Optional[date] = Query(None, alias="to", description="End date (YYYY-MM-DD)"),
    platform: Optional[PlatformEnum] = Query(None, description="Platform filter (IOS, ANDROID, WEB)"),
    authorization: Optional[str] = Header(None, description="Authorization Bearer token"),
    service: AnalyticsProcessingService = Depends(get_processing_service),
):
    try:
        return await service.get_category_breakdown(
            from_date=from_date,
            to_date=to_date,
            platform=platform,
            auth_token=authorization,
        )
    except Exception as err:
        raise _handle_processing_exceptions(err) from err


@router.get(
    "/languages",
    response_model=List[LanguageAggregatedItem],
    summary="Language Metric Breakdown",
    description="Aggregates playback performance grouped across content languages.",
)
async def get_language_breakdown(
    from_date: Optional[date] = Query(None, alias="from", description="Start date (YYYY-MM-DD)"),
    to_date: Optional[date] = Query(None, alias="to", description="End date (YYYY-MM-DD)"),
    platform: Optional[PlatformEnum] = Query(None, description="Platform filter (IOS, ANDROID, WEB)"),
    authorization: Optional[str] = Header(None, description="Authorization Bearer token"),
    service: AnalyticsProcessingService = Depends(get_processing_service),
):
    try:
        return await service.get_language_breakdown(
            from_date=from_date,
            to_date=to_date,
            platform=platform,
            auth_token=authorization,
        )
    except Exception as err:
        raise _handle_processing_exceptions(err) from err
