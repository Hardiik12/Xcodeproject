"""Tests for metadata and contract endpoints."""

import pytest
from httpx import AsyncClient, ASGITransport
from app.main import app


@pytest.mark.asyncio
async def test_metadata_endpoint():
    """Verify metadata endpoint returns expected service details."""
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        response = await client.get("/api/v1/analytics/metadata")

    assert response.status_code == 200
    data = response.json()
    assert data["service"] == "communityott-analytics"
    assert data["version"] == "1.0.0"
    assert data["contract_version"] == "analytics-contract-v1"
    assert data["environment"] == "local"


@pytest.mark.asyncio
async def test_contract_declaration():
    """Verify contract declaration declares Spring Boot producer and Python consumer."""
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        response = await client.get("/api/v1/analytics/contract")

    assert response.status_code == 200
    data = response.json()
    assert data["contract_version"] == "analytics-contract-v1"
    assert data["producer"] == "Spring Boot Monolith"
    assert data["consumer"] == "Python Analytics Service"
    assert data["status"] == "FOUNDATION_READY"
