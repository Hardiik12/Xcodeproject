"""Comprehensive test suite for AnalyticsClient (Phase 7.2)."""

import pytest
import httpx
from datetime import date
from unittest.mock import AsyncMock, patch

from app.clients.analytics_client import AnalyticsClient
from app.clients.errors import (
    AnalyticsClientError,
    AnalyticsAuthenticationError,
    AnalyticsAuthorizationError,
    AnalyticsBadRequestError,
    AnalyticsServerError,
    AnalyticsContractError,
    AnalyticsContractVersionError,
    AnalyticsTimeoutError,
)
from app.core.config import Settings
from app.schemas.contract import AnalyticsExportRecord, AnalyticsExportResponse, PlatformEnum


def get_mock_valid_envelope(
    records=None,
    page=0,
    size=100,
    total_records=1,
    total_pages=1,
    has_next=False,
    contract_version="analytics-contract-v1",
):
    """Generate mock valid analytics-contract-v1 envelope."""
    if records is None:
        records = [
            {
                "date": "2026-08-18",
                "content_id": 101,
                "category_id": 5,
                "language_id": 2,
                "platform": "IOS",
                "sessions": 50,
                "plays": 45,
                "unique_viewers": 30,
                "watch_time_seconds": 9000,
                "completed_plays": 35,
                "completion_rate": 0.7778,
                "buffering_events": 3,
                "playback_errors": 0,
                "quality_changes": 4,
            }
        ]
    return {
        "contract_version": contract_version,
        "generated_at": "2026-08-19T01:00:00Z",
        "from": "2026-08-01",
        "to": "2026-08-18",
        "page": page,
        "size": size,
        "total_records": total_records,
        "total_pages": total_pages,
        "has_next": has_next,
        "records": records,
    }


@pytest.fixture
def mock_settings():
    return Settings(
        SPRING_BOOT_BASE_URL="http://mock-backend:8080",
        ANALYTICS_EXPORT_PATH="/api/v1/analytics/export",
        ANALYTICS_CONTRACT_VERSION="analytics-contract-v1",
        HTTP_TIMEOUT_SECONDS=2.0,
        HTTP_MAX_RETRIES=2,
    )


# 1. Successful page fetch
@pytest.mark.asyncio
async def test_successful_page_fetch(mock_settings):
    payload = get_mock_valid_envelope()
    mock_transport = httpx.MockTransport(lambda req: httpx.Response(200, json=payload))

    async with httpx.AsyncClient(transport=mock_transport, base_url="http://mock-backend:8080") as mock_http:
        client = AnalyticsClient(settings=mock_settings, http_client=mock_http)
        res = await client.fetch_page(from_date=date(2026, 8, 1), to_date=date(2026, 8, 18))

    assert res.contract_version == "analytics-contract-v1"
    assert len(res.records) == 1
    assert res.records[0].content_id == 101
    assert res.records[0].platform == PlatformEnum.IOS
    assert res.records[0].completion_rate == 0.7778


# 2. Successful multi-page fetch
@pytest.mark.asyncio
async def test_successful_multi_page_fetch(mock_settings):
    page0 = get_mock_valid_envelope(page=0, total_pages=2, has_next=True)
    rec2 = [
        {
            "date": "2026-08-18",
            "content_id": 102,
            "category_id": None,
            "language_id": None,
            "platform": "WEB",
            "sessions": 10,
            "plays": 10,
            "unique_viewers": 8,
            "watch_time_seconds": 1200,
            "completed_plays": 5,
            "completion_rate": 0.5000,
            "buffering_events": 0,
            "playback_errors": 0,
            "quality_changes": 0,
        }
    ]
    page1 = get_mock_valid_envelope(records=rec2, page=1, total_pages=2, has_next=False)

    def handler(request: httpx.Request):
        p = int(request.url.params.get("page", 0))
        return httpx.Response(200, json=page0 if p == 0 else page1)

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler), base_url="http://mock-backend:8080") as mock_http:
        client = AnalyticsClient(settings=mock_settings, http_client=mock_http)
        all_records = await client.fetch_all(from_date=date(2026, 8, 1), to_date=date(2026, 8, 18))

    assert len(all_records) == 2
    assert all_records[0].content_id == 101
    assert all_records[1].content_id == 102
    assert all_records[1].platform == PlatformEnum.WEB


# 3. Contract version validation (mismatch rejection)
@pytest.mark.asyncio
async def test_contract_version_mismatch(mock_settings):
    payload = get_mock_valid_envelope(contract_version="analytics-contract-v2")
    mock_transport = httpx.MockTransport(lambda req: httpx.Response(200, json=payload))

    async with httpx.AsyncClient(transport=mock_transport, base_url="http://mock-backend:8080") as mock_http:
        client = AnalyticsClient(settings=mock_settings, http_client=mock_http)
        with pytest.raises(AnalyticsContractVersionError) as exc_info:
            await client.fetch_page()

    assert "Contract version mismatch" in str(exc_info.value)


# 4. Malformed contract rejection (non-dict payload or invalid JSON)
@pytest.mark.asyncio
async def test_malformed_contract_envelope(mock_settings):
    mock_transport = httpx.MockTransport(lambda req: httpx.Response(200, text="Not a JSON object"))

    async with httpx.AsyncClient(transport=mock_transport, base_url="http://mock-backend:8080") as mock_http:
        client = AnalyticsClient(settings=mock_settings, http_client=mock_http)
        with pytest.raises(AnalyticsContractError):
            await client.fetch_page()


# 5. Invalid record rejection (negative metrics or missing required fields)
@pytest.mark.asyncio
async def test_invalid_record_rejection(mock_settings):
    bad_rec = [
        {
            "date": "2026-08-18",
            "content_id": 101,
            "platform": "IOS",
            "sessions": -5,  # Invalid: negative
            "plays": 10,
            "unique_viewers": 10,
            "watch_time_seconds": 100,
            "completed_plays": 5,
            "completion_rate": 0.5,
            "buffering_events": 0,
            "playback_errors": 0,
            "quality_changes": 0,
        }
    ]
    payload = get_mock_valid_envelope(records=bad_rec)
    mock_transport = httpx.MockTransport(lambda req: httpx.Response(200, json=payload))

    async with httpx.AsyncClient(transport=mock_transport, base_url="http://mock-backend:8080") as mock_http:
        client = AnalyticsClient(settings=mock_settings, http_client=mock_http)
        with pytest.raises(AnalyticsContractError) as exc:
            await client.fetch_page()
    assert "validation" in str(exc.value).lower()


# 6. HTTP 400 Handling
@pytest.mark.asyncio
async def test_400_handling(mock_settings):
    mock_transport = httpx.MockTransport(lambda req: httpx.Response(400, json={"error": {"message": "Invalid date range: from must be <= to"}}))

    async with httpx.AsyncClient(transport=mock_transport, base_url="http://mock-backend:8080") as mock_http:
        client = AnalyticsClient(settings=mock_settings, http_client=mock_http)
        with pytest.raises(AnalyticsBadRequestError) as exc:
            await client.fetch_page()
    assert "400 Bad Request" in str(exc.value)


# 7. HTTP 401 Handling
@pytest.mark.asyncio
async def test_401_handling(mock_settings):
    mock_transport = httpx.MockTransport(lambda req: httpx.Response(401, json={"error": "Unauthorized"}))

    async with httpx.AsyncClient(transport=mock_transport, base_url="http://mock-backend:8080") as mock_http:
        client = AnalyticsClient(settings=mock_settings, http_client=mock_http)
        with pytest.raises(AnalyticsAuthenticationError) as exc:
            await client.fetch_page()
    assert "401 Unauthorized" in str(exc.value)


# 8. HTTP 403 Handling
@pytest.mark.asyncio
async def test_403_handling(mock_settings):
    mock_transport = httpx.MockTransport(lambda req: httpx.Response(403, json={"error": "Forbidden"}))

    async with httpx.AsyncClient(transport=mock_transport, base_url="http://mock-backend:8080") as mock_http:
        client = AnalyticsClient(settings=mock_settings, http_client=mock_http)
        with pytest.raises(AnalyticsAuthorizationError) as exc:
            await client.fetch_page()
    assert "403 Forbidden" in str(exc.value)


# 9. HTTP 500 Server Error
@pytest.mark.asyncio
async def test_500_server_error(mock_settings):
    mock_transport = httpx.MockTransport(lambda req: httpx.Response(500, text="Internal Server Error"))

    async with httpx.AsyncClient(transport=mock_transport, base_url="http://mock-backend:8080") as mock_http:
        client = AnalyticsClient(settings=mock_settings, http_client=mock_http)
        with pytest.raises(AnalyticsServerError) as exc:
            await client.fetch_page()
    assert "500" in str(exc.value)


# 10. Timeout Handling
@pytest.mark.asyncio
async def test_timeout_handling(mock_settings):
    def timeout_handler(req):
        raise httpx.ReadTimeout("Request timed out")

    mock_transport = httpx.MockTransport(timeout_handler)

    async with httpx.AsyncClient(transport=mock_transport, base_url="http://mock-backend:8080") as mock_http:
        client = AnalyticsClient(settings=mock_settings, http_client=mock_http)
        with pytest.raises(AnalyticsTimeoutError) as exc:
            await client.fetch_page()
    assert "timeout" in str(exc.value).lower()


# 11. Retry on 503 transient error
@pytest.mark.asyncio
async def test_retry_on_503(mock_settings):
    calls = 0

    def handler(request: httpx.Request):
        nonlocal calls
        calls += 1
        if calls == 1:
            return httpx.Response(503, text="Service Unavailable")
        return httpx.Response(200, json=get_mock_valid_envelope())

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler), base_url="http://mock-backend:8080") as mock_http:
        client = AnalyticsClient(settings=mock_settings, http_client=mock_http)
        res = await client.fetch_page()

    assert calls == 2
    assert res.contract_version == "analytics-contract-v1"


# 12. No retry on 400 bad request
@pytest.mark.asyncio
async def test_no_retry_on_400(mock_settings):
    calls = 0

    def handler(request: httpx.Request):
        nonlocal calls
        calls += 1
        return httpx.Response(400, text="Invalid size")

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler), base_url="http://mock-backend:8080") as mock_http:
        client = AnalyticsClient(settings=mock_settings, http_client=mock_http)
        with pytest.raises(AnalyticsBadRequestError):
            await client.fetch_page()

    assert calls == 1  # Exactly 1 call, zero retries


# 13. Pagination termination
@pytest.mark.asyncio
async def test_pagination_termination(mock_settings):
    page0 = get_mock_valid_envelope(page=0, total_pages=1, has_next=False)

    async with httpx.AsyncClient(transport=httpx.MockTransport(lambda req: httpx.Response(200, json=page0)), base_url="http://mock-backend:8080") as mock_http:
        client = AnalyticsClient(settings=mock_settings, http_client=mock_http)
        records = await client.fetch_all()

    assert len(records) == 1


# 14. Infinite loop cycle detection & max_pages safeguard
@pytest.mark.asyncio
async def test_infinite_loop_protection(mock_settings):
    # has_next is True indefinitely but max_pages terminates it safely
    stuck_page = get_mock_valid_envelope(page=0, has_next=True)

    async with httpx.AsyncClient(transport=httpx.MockTransport(lambda req: httpx.Response(200, json=stuck_page)), base_url="http://mock-backend:8080") as mock_http:
        client = AnalyticsClient(settings=mock_settings, http_client=mock_http)
        records = await client.fetch_all(max_pages=3)

    assert len(records) == 3  # Exactly 3 pages fetched before bounded termination



# 15. Filter forwarding verification
@pytest.mark.asyncio
async def test_filter_forwarding(mock_settings):
    captured_params = {}

    def handler(request: httpx.Request):
        nonlocal captured_params
        captured_params = dict(request.url.params)
        return httpx.Response(200, json=get_mock_valid_envelope())

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler), base_url="http://mock-backend:8080") as mock_http:
        client = AnalyticsClient(settings=mock_settings, http_client=mock_http)
        await client.fetch_page(
            from_date=date(2026, 8, 1),
            to_date=date(2026, 8, 15),
            platform=PlatformEnum.IOS,
            content_id=505,
            category_id=12,
            language_id=3,
            page=2,
            size=50,
        )

    assert captured_params["from"] == "2026-08-01"
    assert captured_params["to"] == "2026-08-15"
    assert captured_params["platform"] == "IOS"
    assert captured_params["content_id"] == "505"
    assert captured_params["category_id"] == "12"
    assert captured_params["language_id"] == "3"
    assert captured_params["page"] == "2"
    assert captured_params["size"] == "50"


# 16. Date serialization in ISO YYYY-MM-DD
@pytest.mark.asyncio
async def test_date_serialization(mock_settings):
    captured_params = {}

    def handler(request: httpx.Request):
        nonlocal captured_params
        captured_params = dict(request.url.params)
        return httpx.Response(200, json=get_mock_valid_envelope())

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler), base_url="http://mock-backend:8080") as mock_http:
        client = AnalyticsClient(settings=mock_settings, http_client=mock_http)
        await client.fetch_page(from_date=date(2026, 1, 5), to_date=date(2026, 12, 31))

    assert captured_params["from"] == "2026-01-05"
    assert captured_params["to"] == "2026-12-31"


# 17. Empty dataset handling
@pytest.mark.asyncio
async def test_empty_dataset(mock_settings):
    payload = get_mock_valid_envelope(records=[], total_records=0, total_pages=0, has_next=False)
    mock_transport = httpx.MockTransport(lambda req: httpx.Response(200, json=payload))

    async with httpx.AsyncClient(transport=mock_transport, base_url="http://mock-backend:8080") as mock_http:
        client = AnalyticsClient(settings=mock_settings, http_client=mock_http)
        res = await client.fetch_page()

    assert res.total_records == 0
    assert len(res.records) == 0
    assert res.has_next is False


# 18. Zero plays handling and completion_rate = 0.0
@pytest.mark.asyncio
async def test_zero_plays_record(mock_settings):
    rec = [
        {
            "date": "2026-08-18",
            "content_id": 999,
            "category_id": None,
            "language_id": None,
            "platform": "ANDROID",
            "sessions": 0,
            "plays": 0,
            "unique_viewers": 0,
            "watch_time_seconds": 0,
            "completed_plays": 0,
            "completion_rate": 0.0,
            "buffering_events": 0,
            "playback_errors": 0,
            "quality_changes": 0,
        }
    ]
    payload = get_mock_valid_envelope(records=rec)
    mock_transport = httpx.MockTransport(lambda req: httpx.Response(200, json=payload))

    async with httpx.AsyncClient(transport=mock_transport, base_url="http://mock-backend:8080") as mock_http:
        client = AnalyticsClient(settings=mock_settings, http_client=mock_http)
        res = await client.fetch_page()

    assert res.records[0].completion_rate == 0.0
    assert res.records[0].plays == 0


# 19. Completion rate upper bound validation (> 1.0 rejected)
@pytest.mark.asyncio
async def test_completion_rate_upper_bound(mock_settings):
    bad_rec = [
        {
            "date": "2026-08-18",
            "content_id": 101,
            "platform": "IOS",
            "sessions": 10,
            "plays": 10,
            "unique_viewers": 10,
            "watch_time_seconds": 100,
            "completed_plays": 15,
            "completion_rate": 1.5,  # Invalid: > 1.0
            "buffering_events": 0,
            "playback_errors": 0,
            "quality_changes": 0,
        }
    ]
    payload = get_mock_valid_envelope(records=bad_rec)
    mock_transport = httpx.MockTransport(lambda req: httpx.Response(200, json=payload))

    async with httpx.AsyncClient(transport=mock_transport, base_url="http://mock-backend:8080") as mock_http:
        client = AnalyticsClient(settings=mock_settings, http_client=mock_http)
        with pytest.raises(AnalyticsContractError):
            await client.fetch_page()


# 20. No sensitive data logging verification
def test_no_auth_header_leak_in_client_repr(mock_settings):
    client = AnalyticsClient(settings=mock_settings)
    headers = client._build_headers(auth_token="super_secret_jwt_token_12345")
    assert headers["Authorization"] == "Bearer super_secret_jwt_token_12345"
    # Ensure client string representation does not expose token
    assert "super_secret_jwt_token_12345" not in repr(client)
