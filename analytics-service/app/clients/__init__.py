"""Clients module for upstream integrations."""

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

__all__ = [
    "AnalyticsClientError",
    "AnalyticsAuthenticationError",
    "AnalyticsAuthorizationError",
    "AnalyticsBadRequestError",
    "AnalyticsServerError",
    "AnalyticsContractError",
    "AnalyticsContractVersionError",
    "AnalyticsTimeoutError",
]
