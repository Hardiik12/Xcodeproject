"""Schemas module for data validation and response models."""

from app.schemas.metadata import HealthResponse, MetadataResponse, ContractDeclaration
from app.schemas.contract import AnalyticsExportRecord, AnalyticsExportResponse, PlatformEnum
from app.schemas.processing import (
    MetricStats,
    ProcessingSummaryResponse,
    ContentPerformanceItem,
    PlatformAggregatedItem,
    CategoryAggregatedItem,
    LanguageAggregatedItem,
)
from app.schemas.advanced import (
    EngagementAnalyticsResponse,
    ContentPerformanceScoreItem,
    GrowthMetricItem,
    GrowthAnalysisResponse,
    DailyTrendItem,
    PlatformComparisonItem,
    CategoryComparisonItem,
    LanguageComparisonItem,
    DistributionMetricItem,
    DistributionAnalysisResponse,
    AnomalyItem,
    AnomalyDetectionResponse,
    InsightItem,
    InsightsResponse,
)

__all__ = [
    "HealthResponse",
    "MetadataResponse",
    "ContractDeclaration",
    "AnalyticsExportRecord",
    "AnalyticsExportResponse",
    "PlatformEnum",
    "MetricStats",
    "ProcessingSummaryResponse",
    "ContentPerformanceItem",
    "PlatformAggregatedItem",
    "CategoryAggregatedItem",
    "LanguageAggregatedItem",
    "EngagementAnalyticsResponse",
    "ContentPerformanceScoreItem",
    "GrowthMetricItem",
    "GrowthAnalysisResponse",
    "DailyTrendItem",
    "PlatformComparisonItem",
    "CategoryComparisonItem",
    "LanguageComparisonItem",
    "DistributionMetricItem",
    "DistributionAnalysisResponse",
    "AnomalyItem",
    "AnomalyDetectionResponse",
    "InsightItem",
    "InsightsResponse",
]
