"""API integration tests for Phase 7.4 Advanced Analytics Endpoints."""

from datetime import date
from unittest.mock import AsyncMock
import pytest
from httpx import ASGITransport, AsyncClient

from app.api.routes.advanced import get_advanced_service
from app.clients.analytics_client import AnalyticsClient
from app.main import app
from app.schemas.contract import AnalyticsExportRecord, PlatformEnum
from app.services.analytics_advanced_service import AnalyticsAdvancedService


@pytest.fixture
def mock_export_records():
    """Mock record dataset for API endpoint testing."""
    return [
        AnalyticsExportRecord(
            content_id=101,
            date=date(2026, 8, 1),
            platform=PlatformEnum.IOS,
            category_id=1,
            language_id=10,
            sessions=100,
            plays=80,
            unique_viewers=50,
            watch_time_seconds=24000,
            completed_plays=40,
            completion_rate=0.50,
            buffering_events=4,
            playback_errors=1,
            quality_changes=5,
        ),
        AnalyticsExportRecord(
            content_id=101,
            date=date(2026, 8, 2),
            platform=PlatformEnum.ANDROID,
            category_id=1,
            language_id=10,
            sessions=150,
            plays=120,
            unique_viewers=80,
            watch_time_seconds=48000,
            completed_plays=96,
            completion_rate=0.80,
            buffering_events=6,
            playback_errors=2,
            quality_changes=10,
        ),
        AnalyticsExportRecord(
            content_id=102,
            date=date(2026, 8, 1),
            platform=PlatformEnum.WEB,
            category_id=2,
            language_id=20,
            sessions=60,
            plays=50,
            unique_viewers=30,
            watch_time_seconds=10000,
            completed_plays=10,
            completion_rate=0.20,
            buffering_events=10,
            playback_errors=5,
            quality_changes=3,
        ),
        AnalyticsExportRecord(
            content_id=103,
            date=date(2026, 8, 3),
            platform=PlatformEnum.ANDROID,
            category_id=None,
            language_id=None,
            sessions=200,
            plays=180,
            unique_viewers=120,
            watch_time_seconds=90000,
            completed_plays=144,
            completion_rate=0.80,
            buffering_events=5,
            playback_errors=1,
            quality_changes=8,
        ),
        AnalyticsExportRecord(
            content_id=104,
            date=date(2026, 8, 4),
            platform=PlatformEnum.WEB,
            category_id=1,
            language_id=10,
            sessions=40,
            plays=30,
            unique_viewers=25,
            watch_time_seconds=6000,
            completed_plays=6,
            completion_rate=0.20,
            buffering_events=8,
            playback_errors=4,
            quality_changes=2,
        ),
    ]


@pytest.fixture
def mock_advanced_service(mock_export_records):
    """AnalyticsAdvancedService with mocked AnalyticsClient."""
    mock_client = AsyncMock(spec=AnalyticsClient)
    mock_client.fetch_all = AsyncMock(return_value=mock_export_records)
    service = AnalyticsAdvancedService(client=mock_client)
    return service


@pytest.mark.asyncio
async def test_api_engagement_endpoint(mock_advanced_service):
    """Test GET /api/v1/analytics/advanced/engagement."""
    app.dependency_overrides[get_advanced_service] = lambda: mock_advanced_service
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        resp = await client.get("/api/v1/analytics/advanced/engagement")
        assert resp.status_code == 200
        data = resp.json()
        assert data["total_sessions"] == 550
        assert data["total_plays"] == 460
        assert data["overall_completion_rate"] > 0.0
        assert "buffering_rate" in data
    app.dependency_overrides.clear()


@pytest.mark.asyncio
async def test_api_content_endpoint(mock_advanced_service):
    """Test GET /api/v1/analytics/advanced/content."""
    app.dependency_overrides[get_advanced_service] = lambda: mock_advanced_service
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        resp = await client.get("/api/v1/analytics/advanced/content?by=performance_score&limit=5")
        assert resp.status_code == 200
        data = resp.json()
        assert len(data) == 4
        assert data[0]["rank"] == 1
        assert "performance_score" in data[0]
    app.dependency_overrides.clear()


@pytest.mark.asyncio
async def test_api_growth_endpoint(mock_advanced_service):
    """Test GET /api/v1/analytics/advanced/growth."""
    app.dependency_overrides[get_advanced_service] = lambda: mock_advanced_service
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        resp = await client.get("/api/v1/analytics/advanced/growth")
        assert resp.status_code == 200
        data = resp.json()
        assert "metrics" in data
        assert "plays" in data["metrics"]
        assert data["metrics"]["plays"]["trend"] in ["UP", "DOWN", "FLAT"]
    app.dependency_overrides.clear()


@pytest.mark.asyncio
async def test_api_trends_endpoint(mock_advanced_service):
    """Test GET /api/v1/analytics/advanced/trends."""
    app.dependency_overrides[get_advanced_service] = lambda: mock_advanced_service
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        resp = await client.get("/api/v1/analytics/advanced/trends")
        assert resp.status_code == 200
        data = resp.json()
        assert len(data) == 4
        assert data[0]["metric_date"] == "2026-08-01"
    app.dependency_overrides.clear()


@pytest.mark.asyncio
async def test_api_platforms_endpoint(mock_advanced_service):
    """Test GET /api/v1/analytics/advanced/platforms."""
    app.dependency_overrides[get_advanced_service] = lambda: mock_advanced_service
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        resp = await client.get("/api/v1/analytics/advanced/platforms")
        assert resp.status_code == 200
        data = resp.json()
        assert len(data) == 3  # IOS, ANDROID, WEB
        total_share = sum(item["share_of_total_plays"] for item in data)
        assert round(total_share, 2) == 1.0
    app.dependency_overrides.clear()


@pytest.mark.asyncio
async def test_api_categories_endpoint(mock_advanced_service):
    """Test GET /api/v1/analytics/advanced/categories."""
    app.dependency_overrides[get_advanced_service] = lambda: mock_advanced_service
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        resp = await client.get("/api/v1/analytics/advanced/categories")
        assert resp.status_code == 200
        data = resp.json()
        assert any(item["category_label"] == "UNASSIGNED" for item in data)
    app.dependency_overrides.clear()


@pytest.mark.asyncio
async def test_api_languages_endpoint(mock_advanced_service):
    """Test GET /api/v1/analytics/advanced/languages."""
    app.dependency_overrides[get_advanced_service] = lambda: mock_advanced_service
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        resp = await client.get("/api/v1/analytics/advanced/languages")
        assert resp.status_code == 200
        data = resp.json()
        assert any(item["language_label"] == "UNASSIGNED" for item in data)
    app.dependency_overrides.clear()


@pytest.mark.asyncio
async def test_api_distributions_endpoint(mock_advanced_service):
    """Test GET /api/v1/analytics/advanced/distributions."""
    app.dependency_overrides[get_advanced_service] = lambda: mock_advanced_service
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        resp = await client.get("/api/v1/analytics/advanced/distributions")
        assert resp.status_code == 200
        data = resp.json()
        assert "plays" in data
        assert "watch_time_seconds" in data
        assert "p25" in data["plays"]
        assert "p95" in data["plays"]
    app.dependency_overrides.clear()


@pytest.mark.asyncio
async def test_api_anomalies_endpoint(mock_advanced_service):
    """Test GET /api/v1/analytics/advanced/anomalies."""
    app.dependency_overrides[get_advanced_service] = lambda: mock_advanced_service
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        resp = await client.get("/api/v1/analytics/advanced/anomalies")
        assert resp.status_code == 200
        data = resp.json()
        assert data["method"] == "IQR"
        assert "anomalies" in data
    app.dependency_overrides.clear()


@pytest.mark.asyncio
async def test_api_insights_endpoint(mock_advanced_service):
    """Test GET /api/v1/analytics/advanced/insights."""
    app.dependency_overrides[get_advanced_service] = lambda: mock_advanced_service
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        resp = await client.get("/api/v1/analytics/advanced/insights")
        assert resp.status_code == 200
        data = resp.json()
        assert "total_insights" in data
        assert "insights" in data
    app.dependency_overrides.clear()


@pytest.mark.asyncio
async def test_api_invalid_date_range(mock_advanced_service):
    """Endpoints must reject invalid date ranges (from > to) with HTTP 400."""
    app.dependency_overrides[get_advanced_service] = lambda: mock_advanced_service
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        resp = await client.get("/api/v1/analytics/advanced/engagement?from=2026-08-10&to=2026-08-01")
        assert resp.status_code == 400
        data = resp.json()
        assert "cannot be after" in data["error"]["message"]
    app.dependency_overrides.clear()


@pytest.mark.asyncio
async def test_api_limit_validation(mock_advanced_service):
    """Content endpoint must enforce 1 <= limit <= 100 with HTTP 422."""
    app.dependency_overrides[get_advanced_service] = lambda: mock_advanced_service
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        resp_too_high = await client.get("/api/v1/analytics/advanced/content?limit=150")
        assert resp_too_high.status_code == 422

        resp_too_low = await client.get("/api/v1/analytics/advanced/content?limit=0")
        assert resp_too_low.status_code == 422
    app.dependency_overrides.clear()
