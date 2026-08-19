"""Internal analytics data verification endpoint consuming Spring Boot via AnalyticsClient."""

from datetime import date
from typing import List, Optional, Union
from fastapi import APIRouter, Depends, Header, HTTPException, Query, status

from app.clients.analytics_client import AnalyticsClient
from app.clients.errors import (
    AnalyticsAuthenticationError,
    AnalyticsAuthorizationError,
    AnalyticsBadRequestError,
    AnalyticsContractError,
    AnalyticsContractVersionError,
    AnalyticsServerError,
    AnalyticsTimeoutError,
)
from app.core.config import Settings, get_settings
from app.schemas.contract import AnalyticsExportRecord, AnalyticsExportResponse, PlatformEnum

router = APIRouter(prefix="/analytics", tags=["Analytics Data Consumer"])


def get_analytics_client(settings: Settings = Depends(get_settings)) -> AnalyticsClient:
    """Dependency provider for AnalyticsClient."""
    return AnalyticsClient(settings=settings)


@router.get(
    "/data",
    response_model=Union[AnalyticsExportResponse, List[AnalyticsExportRecord]],
    summary="Fetch Validated Analytics Data",
    description=(
        "Internal verification endpoint that consumes GET /api/v1/analytics/export from Spring Boot, "
        "validates the payload against analytics-contract-v1, and returns the strongly-typed dataset."
    ),
)
async def get_analytics_data(
    from_date: Optional[date] = Query(None, alias="from", description="Start date (YYYY-MM-DD)"),
    to_date: Optional[date] = Query(None, alias="to", description="End date (YYYY-MM-DD)"),
    platform: Optional[PlatformEnum] = Query(None, description="Platform filter (IOS, ANDROID, WEB)"),
    content_id: Optional[int] = Query(None, description="Content ID filter"),
    category_id: Optional[int] = Query(None, description="Category ID filter"),
    language_id: Optional[int] = Query(None, description="Language ID filter"),
    page: int = Query(0, ge=0, description="Page index (0-indexed)"),
    size: int = Query(100, ge=1, le=100, description="Page size (max 100)"),
    fetch_all: bool = Query(False, description="If true, fetches and aggregates all pages"),
    authorization: Optional[str] = Header(None, description="Optional forwarded Authorization Bearer token"),
    client: AnalyticsClient = Depends(get_analytics_client),
):
    """Retrieve and validate analytics data from Spring Boot."""
    try:
        if fetch_all:
            records = await client.fetch_all(
                from_date=from_date,
                to_date=to_date,
                platform=platform,
                content_id=content_id,
                category_id=category_id,
                language_id=language_id,
                page_size=size,
                auth_token=authorization,
            )
            return records

        return await client.fetch_page(
            from_date=from_date,
            to_date=to_date,
            platform=platform,
            content_id=content_id,
            category_id=category_id,
            language_id=language_id,
            page=page,
            size=size,
            auth_token=authorization,
        )

    except AnalyticsBadRequestError as err:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(err)) from err
    except AnalyticsAuthenticationError as err:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail=str(err)) from err
    except AnalyticsAuthorizationError as err:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(err)) from err
    except (AnalyticsContractVersionError, AnalyticsContractError) as err:
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail=f"Contract validation failure: {err}") from err
    except AnalyticsTimeoutError as err:
        raise HTTPException(status_code=status.HTTP_504_GATEWAY_TIMEOUT, detail=str(err)) from err
    except AnalyticsServerError as err:
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail=f"Upstream server error: {err}") from err
