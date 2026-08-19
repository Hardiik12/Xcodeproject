"""Tests for error handling, CORS, and 404 responses."""

import pytest
from httpx import AsyncClient, ASGITransport
from app.main import app


@pytest.mark.asyncio
async def test_404_error_handling():
    """Verify 404 error returns consistent JSON error schema without stack traces."""
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        response = await client.get("/api/v1/analytics/nonexistent-endpoint")

    assert response.status_code == 404
    data = response.json()
    assert data["success"] is False
    assert data["error"]["code"] == "NOT_FOUND"
    assert "message" in data["error"]
    assert "traceback" not in data
    assert "stack" not in data


@pytest.mark.asyncio
async def test_cors_headers():
    """Verify CORS middleware sets appropriate headers for allowed origins."""
    headers = {
        "Origin": "http://localhost:3000",
        "Access-Control-Request-Method": "GET",
    }
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        response = await client.options("/api/v1/analytics/health", headers=headers)

    assert response.status_code == 200
    assert response.headers.get("access-control-allow-origin") == "http://localhost:3000"


@pytest.mark.asyncio
async def test_openapi_schema_available():
    """Verify OpenAPI specification and documentation are accessible."""
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        response = await client.get("/openapi.json")

    assert response.status_code == 200
    schema = response.json()
    assert "openapi" in schema
    assert schema["info"]["title"] == "CommunityOTT Analytics Service"
    assert "/api/v1/analytics/health" in schema["paths"]
    assert "/api/v1/analytics/metadata" in schema["paths"]
