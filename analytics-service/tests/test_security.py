"""Security boundary tests verifying zero secret leakage and zero direct DB access."""

import pytest
from httpx import AsyncClient, ASGITransport
from app.main import app
from app.core.config import get_settings


@pytest.mark.asyncio
async def test_no_secret_leakage_in_metadata():
    """Verify that metadata endpoint contains no credentials, passwords, or tokens."""
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        response = await client.get("/api/v1/analytics/metadata")

    text_content = response.text.lower()
    for forbidden in ["password", "secret", "token", "postgres", "redis", "minio", "private_key"]:
        assert forbidden not in text_content, f"Forbidden term '{forbidden}' leaked in metadata response"


def test_settings_no_database_credentials():
    """Verify that settings model contains no database or operational credentials."""
    settings = get_settings()
    settings_dict = settings.model_dump()

    for forbidden_key in [
        "POSTGRES_USER", "POSTGRES_PASSWORD", "POSTGRES_DB", "DATABASE_URL",
        "REDIS_PASSWORD", "MINIO_ROOT_PASSWORD", "JWT_SECRET", "OTP_SECRET"
    ]:
        assert forbidden_key not in settings_dict, f"Settings must not define '{forbidden_key}'"
