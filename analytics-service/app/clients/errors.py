"""Dedicated exceptions for Spring Boot Analytics Export HTTP Client."""


class AnalyticsClientError(Exception):
    """Base exception for all analytics client errors."""
    pass


class AnalyticsAuthenticationError(AnalyticsClientError):
    """Raised when Spring Boot returns 401 Unauthorized."""
    pass


class AnalyticsAuthorizationError(AnalyticsClientError):
    """Raised when Spring Boot returns 403 Forbidden (missing ANALYTICS_VIEW permission)."""
    pass


class AnalyticsBadRequestError(AnalyticsClientError):
    """Raised when Spring Boot returns 400 Bad Request (invalid date range, size > 100, etc)."""
    pass


class AnalyticsServerError(AnalyticsClientError):
    """Raised when Spring Boot returns a 5xx Server Error."""
    pass


class AnalyticsContractError(AnalyticsClientError):
    """Raised when response payload violates Pydantic contract validation rules."""
    pass


class AnalyticsContractVersionError(AnalyticsContractError):
    """Raised when response contract_version does not match expected analytics-contract-v1."""
    pass


class AnalyticsTimeoutError(AnalyticsClientError):
    """Raised when request times out."""
    pass
