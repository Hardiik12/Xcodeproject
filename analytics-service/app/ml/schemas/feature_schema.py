"""Pydantic schemas and version declarations for Phase 7.5 ML Feature Engineering."""

from datetime import date, datetime
from typing import List, Optional
from pydantic import BaseModel, ConfigDict, Field

FEATURE_SCHEMA_VERSION = "features-v1"


class FeatureMetadataItem(BaseModel):
    """Metadata specification for an individual engineered feature."""

    model_config = ConfigDict(extra="forbid")

    name: str = Field(description="Unique feature identifier name")
    type: str = Field(description="Feature data type (numeric, categorical, boolean, datetime)")
    category: str = Field(description="Feature group (engagement, temporal, content, lag, rolling, growth, platform)")
    description: str = Field(description="Concise description of the feature's analytical meaning")
    source: str = Field(description="Origin column(s) or contract derivation source")
    formula: str = Field(description="Exact mathematical or operational formula")
    nullable: bool = Field(description="Whether the feature allows null/missing values before imputation")
    allowed_range: str = Field(description="Valid boundary or allowed categories (e.g. '>= 0', '[0.0, 1.0]', 'IOS, ANDROID, WEB')")
    future_leakage_risk: bool = Field(description="True if feature has risk of target/future lookahead (must be False for inputs)")


class FeatureMetadataResponse(BaseModel):
    """Envelope response for GET /api/v1/ml/features/metadata."""

    model_config = ConfigDict(populate_by_name=True, extra="forbid")

    feature_schema_version: str = Field(
        default=FEATURE_SCHEMA_VERSION,
        description="Version identifier of the feature engineering schema",
    )
    source_contract_version: str = Field(
        description="Version identifier of the underlying source contract",
    )
    generated_at: datetime = Field(
        description="UTC timestamp when the feature metadata was generated",
    )
    from_date: Optional[date] = Field(
        default=None,
        alias="from",
        description="Start date of the analyzed dataset interval",
    )
    to_date: Optional[date] = Field(
        default=None,
        alias="to",
        description="End date of the analyzed dataset interval",
    )
    row_count: int = Field(
        description="Total observations in the source dataset",
    )
    feature_count: int = Field(
        description="Total count of engineered feature columns",
    )
    features: List[FeatureMetadataItem] = Field(
        description="Detailed registry of engineered features",
    )
