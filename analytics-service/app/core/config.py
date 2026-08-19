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

    @property
    def cors_origins(self) -> List[str]:
        """Parse comma-separated allowed origins into a list."""
        return [origin.strip() for origin in self.ALLOWED_ORIGINS.split(",") if origin.strip()]


@lru_cache()
def get_settings() -> Settings:
    """Return cached settings instance."""
    return Settings()
