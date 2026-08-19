"""Tests for health endpoint."""

import pytest
from httpx import AsyncClient, ASGITransport
from app.main import app


@pytest.mark.asyncio
async def test_health_endpoint():
    """Verify health endpoint returns UP status and required envelope."""
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        response = await client.get("/api/v1/analytics/health")

    assert response.status_code == 200
    data = response.json()
    assert data["success"] is True
    assert data["status"] == "UP"
    assert data["service"] == "communityott-analytics"
    assert "version" in data
