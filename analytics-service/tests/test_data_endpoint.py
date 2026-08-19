"""Tests for the internal verification endpoint GET /api/v1/analytics/data."""

import pytest
from httpx import AsyncClient, ASGITransport
from datetime import date
from unittest.mock import AsyncMock

from app.main import app
from app.api.routes.data import get_analytics_client
from app.schemas.contract import AnalyticsExportRecord, AnalyticsExportResponse, PlatformEnum
from app.clients.errors import AnalyticsBadRequestError, AnalyticsAuthenticationError, AnalyticsContractVersionError


@pytest.fixture
def mock_export_response():
    return AnalyticsExportResponse(
        contract_version="analytics-contract-v1",
        generated_at="2026-08-19T01:00:00Z",
        from_date=date(2026, 8, 1),
        to_date=date(2026, 8, 18),
        page=0,
        size=100,
        total_records=1,
        total_pages=1,
        has_next=False,
        records=[
            AnalyticsExportRecord(
                date=date(2026, 8, 18),
                content_id=101,
                category_id=5,
                language_id=2,
                platform=PlatformEnum.IOS,
                sessions=50,
                plays=45,
                unique_viewers=30,
                watch_time_seconds=9000,
                completed_plays=35,
                completion_rate=0.7778,
                buffering_events=3,
                playback_errors=0,
                quality_changes=4,
            )
        ],
    )


@pytest.mark.asyncio
async def test_data_endpoint_success(mock_export_response):
    mock_client = AsyncMock()
    mock_client.fetch_page.return_value = mock_export_response

    app.dependency_overrides[get_analytics_client] = lambda: mock_client

    try:
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.get("/api/v1/analytics/data?from=2026-08-01&to=2026-08-18&platform=IOS")

        assert response.status_code == 200
        data = response.json()
        assert data["contract_version"] == "analytics-contract-v1"
        assert len(data["records"]) == 1
        assert data["records"][0]["content_id"] == 101
    finally:
        app.dependency_overrides.clear()


@pytest.mark.asyncio
async def test_data_endpoint_fetch_all(mock_export_response):
    mock_client = AsyncMock()
    mock_client.fetch_all.return_value = mock_export_response.records

    app.dependency_overrides[get_analytics_client] = lambda: mock_client

    try:
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.get("/api/v1/analytics/data?fetch_all=true")

        assert response.status_code == 200
        data = response.json()
        assert isinstance(data, list)
        assert len(data) == 1
        assert data[0]["content_id"] == 101
    finally:
        app.dependency_overrides.clear()


@pytest.mark.asyncio
async def test_data_endpoint_bad_request():
    mock_client = AsyncMock()
    mock_client.fetch_page.side_effect = AnalyticsBadRequestError("Invalid date range")

    app.dependency_overrides[get_analytics_client] = lambda: mock_client

    try:
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.get("/api/v1/analytics/data")

        assert response.status_code == 400
        assert "Invalid date range" in response.json()["error"]["message"]
    finally:
        app.dependency_overrides.clear()


@pytest.mark.asyncio
async def test_data_endpoint_unauthorized():
    mock_client = AsyncMock()
    mock_client.fetch_page.side_effect = AnalyticsAuthenticationError("Missing auth")

    app.dependency_overrides[get_analytics_client] = lambda: mock_client

    try:
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.get("/api/v1/analytics/data")

        assert response.status_code == 401
        assert "Missing auth" in response.json()["error"]["message"]
    finally:
        app.dependency_overrides.clear()


@pytest.mark.asyncio
async def test_data_endpoint_contract_version_mismatch():
    mock_client = AsyncMock()
    mock_client.fetch_page.side_effect = AnalyticsContractVersionError("Version mismatch")

    app.dependency_overrides[get_analytics_client] = lambda: mock_client

    try:
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.get("/api/v1/analytics/data")

        assert response.status_code == 502
        assert "Contract validation failure" in response.json()["error"]["message"]
    finally:
        app.dependency_overrides.clear()
