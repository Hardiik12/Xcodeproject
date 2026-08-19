"""CommunityOTT Python Analytics Service - Application Factory."""

from contextlib import asynccontextmanager
from typing import AsyncGenerator
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.routes.health import router as health_router
from app.api.routes.metadata import router as metadata_router
from app.api.routes.data import router as data_router
from app.core.config import get_settings
from app.core.errors import register_error_handlers
from app.core.logging import setup_logging


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncGenerator[None, None]:
    """Application startup and graceful shutdown events."""
    settings = get_settings()
    logger = setup_logging(settings.LOG_LEVEL)
    logger.info(
        f"Starting {settings.APP_NAME} v{settings.APP_VERSION} "
        f"[env={settings.ENVIRONMENT}, contract={settings.ANALYTICS_CONTRACT_VERSION}]"
    )
    yield
    logger.info(f"Stopping {settings.APP_NAME}")


def create_app() -> FastAPI:
    """Build and configure the FastAPI application instance."""
    settings = get_settings()

    app = FastAPI(
        title="CommunityOTT Analytics Service",
        description=(
            "Independent Python/FastAPI microservice for advanced analytics, "
            "statistics, and future machine learning processing consuming "
            f"the {settings.ANALYTICS_CONTRACT_VERSION} contract from the Spring Boot Monolith."
        ),
        version=settings.APP_VERSION,
        docs_url="/docs",
        redoc_url="/redoc",
        openapi_url="/openapi.json",
        lifespan=lifespan,
    )

    # CORS configuration
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins,
        allow_credentials=True,
        allow_methods=["GET", "POST", "OPTIONS"],
        allow_headers=["*"],
    )

    # Centralized exception handlers
    register_error_handlers(app)

    # Register API routes under /api/v1
    app.include_router(health_router, prefix="/api/v1")
    app.include_router(metadata_router, prefix="/api/v1")
    app.include_router(data_router, prefix="/api/v1")

    return app


app = create_app()
