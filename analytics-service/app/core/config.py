"""Application configuration using Pydantic Settings."""

from functools import lru_cache
from typing import List, Optional
from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Type-safe application settings loaded from environment or defaults."""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=True,
        extra="ignore",
    )

    APP_NAME: str = Field(default="communityott-analytics", description="Application service name")
    APP_VERSION: str = Field(default="1.0.0", description="Application semantic version")
    ENVIRONMENT: str = Field(default="local", description="Runtime environment (local, dev, staging, prod)")
    LOG_LEVEL: str = Field(default="INFO", description="Logging level (DEBUG, INFO, WARNING, ERROR)")
    HOST: str = Field(default="0.0.0.0", description="Server bind host")
    PORT: int = Field(default=8001, description="Server bind port")
    ALLOWED_ORIGINS: str = Field(
        default="http://localhost:3000,http://localhost:5173,http://localhost:8080",
        description="Comma-separated CORS allowed origins",
    )
    SPRING_BOOT_BASE_URL: str = Field(
        default="http://localhost:8080",
        description="Base URL for the upstream Spring Boot Monolith backend",
    )
    ANALYTICS_EXPORT_PATH: str = Field(
        default="/api/v1/analytics/export",
        description="Endpoint path for analytics export on Spring Boot",
    )
    ANALYTICS_CONTRACT_VERSION: str = Field(
        default="analytics-contract-v1",
        description="Agreed Analytics Data Contract specification version",
    )
    HTTP_TIMEOUT_SECONDS: float = Field(
        default=10.0,
        description="HTTP client timeout in seconds",
    )
    HTTP_MAX_RETRIES: int = Field(
        default=2,
        description="Maximum retry attempts for transient HTTP failures",
    )
    DEV_AUTH_TOKEN: Optional[str] = Field(
        default=None,
        description="Optional Bearer token or Dev user token for local development integration testing",
    )
    MODEL_ARTIFACT_DIR: str = Field(
        default="artifacts/models",
        description="Root directory for versioned ML model artifacts and registry manifest",
    )

    # Phase 7.4 Analytics Heuristic Thresholds
    HIGH_BUFFERING_RATE_THRESHOLD: float = Field(
        default=0.05,
        description="Buffering rate threshold above which HIGH_BUFFERING insight triggers (5%)",
    )
    HIGH_ERROR_RATE_THRESHOLD: float = Field(
        default=0.02,
        description="Playback error rate threshold above which HIGH_ERROR_RATE insight triggers (2%)",
    )
    LOW_COMPLETION_RATE_THRESHOLD: float = Field(
        default=0.30,
        description="Completion rate threshold below which LOW_COMPLETION insight triggers (30%)",
    )
    HIGH_ENGAGEMENT_COMPLETION_THRESHOLD: float = Field(
        default=0.70,
        description="Completion rate threshold above which HIGH_ENGAGEMENT insight triggers (70%)",
    )
    GROWTH_ALERT_THRESHOLD: float = Field(
        default=25.0,
        description="Period-over-period growth percentage threshold for RAPID_GROWTH insight (25%)",
    )
    DECLINING_CONTENT_THRESHOLD: float = Field(
        default=-20.0,
        description="Period-over-period growth percentage threshold for DECLINING_CONTENT insight (-20%)",
    )

    @property
    def cors_origins(self) -> List[str]:
        """Parse comma-separated allowed origins into a list."""
        return [origin.strip() for origin in self.ALLOWED_ORIGINS.split(",") if origin.strip()]


@lru_cache()
def get_settings() -> Settings:
    """Return cached settings instance."""
    return Settings()
