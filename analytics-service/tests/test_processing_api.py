"""API endpoint integration and contract tests for analytics processing routes."""

import pytest
from httpx import AsyncClient, ASGITransport
import datetime as dt
from unittest.mock import AsyncMock

from app.main import app
from app.api.routes.processing import get_processing_service
from app.processing import build_dataframe
from app.schemas.contract import AnalyticsExportRecord, PlatformEnum
from app.schemas.processing import (
    CategoryAggregatedItem,
    ContentPerformanceItem,
    LanguageAggregatedItem,
    MetricStats,
    PlatformAggregatedItem,
    ProcessingSummaryResponse,
)


def mock_records():
    return [
        AnalyticsExportRecord(
            date=dt.date(2026, 8, 18),
            content_id=101,
            category_id=5,
            language_id=2,
            platform=PlatformEnum.IOS,
            sessions=100,
            plays=90,
            unique_viewers=80,
            watch_time_seconds=18000,
            completed_plays=45,
            completion_rate=0.5000,
            buffering_events=4,
            playback_errors=0,
            quality_changes=12,
        ),
        AnalyticsExportRecord(
            date=dt.date(2026, 8, 18),
            content_id=102,
            category_id=6,
            language_id=2,
            platform=PlatformEnum.ANDROID,
            sessions=50,
            plays=40,
            unique_viewers=35,
            watch_time_seconds=8000,
            completed_plays=20,
            completion_rate=0.5000,
            buffering_events=2,
            playback_errors=1,
            quality_changes=5,
        ),
    ]


@pytest.mark.asyncio
async def test_processing_summary_endpoint():
    mock_service = AsyncMock()
    mock_service.get_summary.return_value = ProcessingSummaryResponse(
        contract_version="analytics-contract-v1",
        total_records=2,
        distinct_contents=2,
        statistics={
            "plays": MetricStats(
                count=2,
                mean=65.0,
                median=65.0,
                minimum=40.0,
                maximum=90.0,
                standard_deviation=35.3553,
            )
        },
    )

    app.dependency_overrides[get_processing_service] = lambda: mock_service

    try:
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.get("/api/v1/analytics/processing/summary?from=2026-08-01&to=2026-08-18")

        assert response.status_code == 200
        data = response.json()
        assert data["contract_version"] == "analytics-contract-v1"
        assert data["total_records"] == 2
        assert "plays" in data["statistics"]
        assert data["statistics"]["plays"]["mean"] == 65.0
    finally:
        app.dependency_overrides.clear()


@pytest.mark.asyncio
async def test_processing_content_endpoint():
    mock_service = AsyncMock()
    mock_service.get_content_performance.return_value = [
        ContentPerformanceItem(
            content_id=101,
            sessions=100,
            plays=90,
            unique_viewers=80,
            watch_time_seconds=18000,
            completed_plays=45,
            completion_rate=0.5000,
            buffering_events=4,
            playback_errors=0,
            quality_changes=12,
        )
    ]

    app.dependency_overrides[get_processing_service] = lambda: mock_service

    try:
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.get("/api/v1/analytics/processing/content?by=plays&limit=5")

        assert response.status_code == 200
        data = response.json()
        assert isinstance(data, list)
        assert len(data) == 1
        assert data[0]["content_id"] == 101
        assert data[0]["plays"] == 90
    finally:
        app.dependency_overrides.clear()


@pytest.mark.asyncio
async def test_processing_platforms_endpoint():
    mock_service = AsyncMock()
    mock_service.get_platform_breakdown.return_value = [
        PlatformAggregatedItem(
            platform="IOS",
            sessions=100,
            plays=90,
            unique_viewers=80,
            watch_time_seconds=18000,
            completed_plays=45,
            completion_rate=0.5000,
            buffering_events=4,
            playback_errors=0,
            quality_changes=12,
        ),
        PlatformAggregatedItem(
            platform="ANDROID",
            sessions=50,
            plays=40,
            unique_viewers=35,
            watch_time_seconds=8000,
            completed_plays=20,
            completion_rate=0.5000,
            buffering_events=2,
            playback_errors=1,
            quality_changes=5,
        ),
    ]

    app.dependency_overrides[get_processing_service] = lambda: mock_service

    try:
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.get("/api/v1/analytics/processing/platforms")

        assert response.status_code == 200
        data = response.json()
        assert len(data) == 2
        assert data[0]["platform"] == "IOS"
        assert data[1]["platform"] == "ANDROID"
    finally:
        app.dependency_overrides.clear()


@pytest.mark.asyncio
async def test_processing_categories_endpoint():
    mock_service = AsyncMock()
    mock_service.get_category_breakdown.return_value = [
        CategoryAggregatedItem(
            category_id=5,
            sessions=100,
            plays=90,
            unique_viewers=80,
            watch_time_seconds=18000,
            completed_plays=45,
            completion_rate=0.5000,
            buffering_events=4,
            playback_errors=0,
            quality_changes=12,
        )
    ]

    app.dependency_overrides[get_processing_service] = lambda: mock_service

    try:
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.get("/api/v1/analytics/processing/categories")

        assert response.status_code == 200
        data = response.json()
        assert len(data) == 1
        assert data[0]["category_id"] == 5
    finally:
        app.dependency_overrides.clear()


@pytest.mark.asyncio
async def test_processing_languages_endpoint():
    mock_service = AsyncMock()
    mock_service.get_language_breakdown.return_value = [
        LanguageAggregatedItem(
            language_id=2,
            sessions=100,
            plays=90,
            unique_viewers=80,
            watch_time_seconds=18000,
            completed_plays=45,
            completion_rate=0.5000,
            buffering_events=4,
            playback_errors=0,
            quality_changes=12,
        )
    ]

    app.dependency_overrides[get_processing_service] = lambda: mock_service

    try:
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.get("/api/v1/analytics/processing/languages")

        assert response.status_code == 200
        data = response.json()
        assert len(data) == 1
        assert data[0]["language_id"] == 2
    finally:
        app.dependency_overrides.clear()
