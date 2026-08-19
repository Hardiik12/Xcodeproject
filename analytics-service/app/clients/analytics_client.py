"""Asynchronous HTTP Client consuming Spring Boot analytics-contract-v1 export API."""

import asyncio
import logging
from datetime import date
from typing import Any, Dict, List, Optional, Set, Union
import httpx
from pydantic import ValidationError

from app.core.config import Settings, get_settings
from app.schemas.contract import AnalyticsExportRecord, AnalyticsExportResponse, PlatformEnum
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

logger = logging.getLogger("communityott.analytics.client")


class AnalyticsClient:
    """Async HTTP Client for consuming Spring Boot GET /api/v1/analytics/export."""

    def __init__(
        self,
        settings: Optional[Settings] = None,
        http_client: Optional[httpx.AsyncClient] = None,
    ):
        self.settings = settings or get_settings()
        self._custom_client = http_client

    def _get_client(self) -> httpx.AsyncClient:
        """Return injected client or instantiate a configured httpx.AsyncClient."""
        if self._custom_client is not None:
            return self._custom_client
        return httpx.AsyncClient(
            base_url=self.settings.SPRING_BOOT_BASE_URL,
            timeout=self.settings.HTTP_TIMEOUT_SECONDS,
        )

    def _build_params(
        self,
        from_date: Optional[date] = None,
        to_date: Optional[date] = None,
        platform: Optional[Union[PlatformEnum, str]] = None,
        content_id: Optional[int] = None,
        category_id: Optional[int] = None,
        language_id: Optional[int] = None,
        page: int = 0,
        size: int = 100,
    ) -> Dict[str, Any]:
        """Construct exact query parameters conforming to Spring Boot export contract."""
        params: Dict[str, Any] = {
            "page": page,
            "size": min(size, 100),
        }
        if from_date is not None:
            params["from"] = from_date.strftime("%Y-%m-%d")
        if to_date is not None:
            params["to"] = to_date.strftime("%Y-%m-%d")
        if platform is not None:
            params["platform"] = platform.value if isinstance(platform, PlatformEnum) else str(platform).upper()
        if content_id is not None:
            params["content_id"] = content_id
        if category_id is not None:
            params["category_id"] = category_id
        if language_id is not None:
            params["language_id"] = language_id

        return params

    def _build_headers(self, auth_token: Optional[str] = None) -> Dict[str, str]:
        """Construct authorization headers without leaking sensitive tokens in logs."""
        headers: Dict[str, str] = {
            "Accept": "application/json",
            "User-Agent": f"CommunityOTT-AnalyticsClient/{self.settings.APP_VERSION}",
        }
        token = auth_token or self.settings.DEV_AUTH_TOKEN
        if token:
            if token.startswith("Bearer "):
                headers["Authorization"] = token
            elif token.isdigit():
                headers["X-Dev-User-Id"] = token
            else:
                headers["Authorization"] = f"Bearer {token}"
        return headers

    async def fetch_page(
        self,
        from_date: Optional[date] = None,
        to_date: Optional[date] = None,
        platform: Optional[Union[PlatformEnum, str]] = None,
        content_id: Optional[int] = None,
        category_id: Optional[int] = None,
        language_id: Optional[int] = None,
        page: int = 0,
        size: int = 100,
        auth_token: Optional[str] = None,
    ) -> AnalyticsExportResponse:
        """Fetch and validate a single page of export metrics from Spring Boot."""
        params = self._build_params(
            from_date=from_date,
            to_date=to_date,
            platform=platform,
            content_id=content_id,
            category_id=category_id,
            language_id=language_id,
            page=page,
            size=size,
        )
        headers = self._build_headers(auth_token=auth_token)
        url = self.settings.ANALYTICS_EXPORT_PATH

        retries = 0
        max_retries = self.settings.HTTP_MAX_RETRIES

        client = self._get_client()
        should_close = self._custom_client is None

        try:
            while True:
                try:
                    logger.debug(f"Fetching export page {page} with params: {params}")
                    response = await client.get(url, params=params, headers=headers)

                    if response.status_code == 200:
                        try:
                            raw_data = response.json()
                        except Exception as json_err:
                            raise AnalyticsContractError(f"Malformed non-JSON response payload: {json_err}") from json_err
                        return self._parse_and_validate(raw_data)

                    # Explicit error mapping without retrying client errors
                    if response.status_code == 400:
                        msg = response.text
                        try:
                            msg = response.json().get("error", {}).get("message", response.text)
                        except Exception:
                            pass
                        raise AnalyticsBadRequestError(f"Spring Boot returned 400 Bad Request: {msg}")

                    if response.status_code == 401:
                        raise AnalyticsAuthenticationError("Spring Boot returned 401 Unauthorized: Invalid or missing authentication")

                    if response.status_code == 403:
                        raise AnalyticsAuthorizationError("Spring Boot returned 403 Forbidden: Missing ANALYTICS_VIEW permission")

                    if response.status_code in (500, 502, 503, 504):
                        if retries < max_retries:
                            retries += 1
                            wait_time = 0.2 * (2 ** retries)
                            logger.warning(f"Transient HTTP {response.status_code} error from Spring Boot. Retrying in {wait_time}s (attempt {retries}/{max_retries})")
                            await asyncio.sleep(wait_time)
                            continue
                        raise AnalyticsServerError(f"Spring Boot returned HTTP {response.status_code}: {response.text}")

                    raise AnalyticsClientError(f"Unexpected HTTP {response.status_code} from Spring Boot: {response.text}")

                except (httpx.TimeoutException, httpx.ConnectError, httpx.NetworkError) as err:
                    if retries < max_retries:
                        retries += 1
                        wait_time = 0.2 * (2 ** retries)
                        logger.warning(f"Network error communicating with Spring Boot: {err}. Retrying in {wait_time}s (attempt {retries}/{max_retries})")
                        await asyncio.sleep(wait_time)
                        continue
                    raise AnalyticsTimeoutError(f"Connection or timeout error reaching Spring Boot: {err}") from err

        finally:
            if should_close:
                await client.aclose()

    def _parse_and_validate(self, raw_data: Any) -> AnalyticsExportResponse:
        """Validate envelope structure, contract version, and record schemas."""
        if not isinstance(raw_data, dict):
            raise AnalyticsContractError(f"Expected JSON object envelope from export API, got {type(raw_data).__name__}")

        # Strict contract version check
        contract_version = raw_data.get("contract_version")
        if contract_version != self.settings.ANALYTICS_CONTRACT_VERSION:
            raise AnalyticsContractVersionError(
                f"Contract version mismatch: expected '{self.settings.ANALYTICS_CONTRACT_VERSION}', "
                f"received '{contract_version}'"
            )

        try:
            return AnalyticsExportResponse.model_validate(raw_data)
        except ValidationError as err:
            logger.error(f"Failed to validate contract response payload: {err}")
            raise AnalyticsContractError(f"Response failed analytics-contract-v1 validation: {err}") from err

    async def fetch_all(
        self,
        from_date: Optional[date] = None,
        to_date: Optional[date] = None,
        platform: Optional[Union[PlatformEnum, str]] = None,
        content_id: Optional[int] = None,
        category_id: Optional[int] = None,
        language_id: Optional[int] = None,
        page_size: int = 100,
        max_pages: int = 500,
        auth_token: Optional[str] = None,
    ) -> List[AnalyticsExportRecord]:
        """Fetch and aggregate all pages of export records with infinite-loop safeguards."""
        all_records: List[AnalyticsExportRecord] = []
        current_page = 0
        visited_pages: Set[int] = set()

        while True:
            if current_page in visited_pages:
                raise AnalyticsContractError(f"Pagination cycle detected: page {current_page} requested multiple times")
            if current_page >= max_pages:
                logger.warning(f"Reached safety max_pages limit ({max_pages}). Terminating pagination.")
                break

            visited_pages.add(current_page)

            page_response = await self.fetch_page(
                from_date=from_date,
                to_date=to_date,
                platform=platform,
                content_id=content_id,
                category_id=category_id,
                language_id=language_id,
                page=current_page,
                size=page_size,
                auth_token=auth_token,
            )

            all_records.extend(page_response.records)

            if not page_response.has_next:
                break

            current_page += 1

        return all_records
