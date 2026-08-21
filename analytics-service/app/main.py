"""CommunityOTT Python Analytics Service - Application Factory."""

from contextlib import asynccontextmanager
import logging
import time
from typing import AsyncGenerator
from uuid import uuid4

from fastapi import FastAPI, Request, Response
from fastapi.middleware.cors import CORSMiddleware

from app.api.routes.advanced import router as advanced_router
from app.api.routes.data import router as data_router
from app.api.routes.health import router as health_router
from app.api.routes.metadata import router as metadata_router
from app.api.routes.ml import router as ml_router
from app.api.routes.processing import router as processing_router
from app.core.config import get_settings
from app.core.errors import register_error_handlers
from app.core.logging import setup_logging

logger = logging.getLogger("app.main")


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncGenerator[None, None]:
    """Application startup and graceful shutdown events."""
    settings = get_settings()
    log = setup_logging(settings.LOG_LEVEL)
    log.info(
        f"Starting {settings.APP_NAME} v{settings.APP_VERSION} "
        f"[env={settings.ENVIRONMENT}, contract={settings.ANALYTICS_CONTRACT_VERSION}]"
    )
    yield
    log.info(f"Stopping {settings.APP_NAME}")


def create_app() -> FastAPI:
    """Build and configure the FastAPI application instance."""
    settings = get_settings()

    app = FastAPI(
        title="CommunityOTT Analytics Service",
        description=(
            "Independent Python/FastAPI microservice for advanced analytics, "
            "statistics, and deterministic calculations consuming "
            f"the {settings.ANALYTICS_CONTRACT_VERSION} contract from the Spring Boot Monolith."
        ),
        version=settings.APP_VERSION,
        docs_url="/docs",
        redoc_url="/redoc",
        openapi_url="/openapi.json",
        lifespan=lifespan,
    )

    # Request ID and Structured Logging Middleware
    @app.middleware("http")
    async def request_id_and_logging_middleware(request: Request, call_next) -> Response:
        start_time = time.perf_counter()
        req_id = request.headers.get("X-Request-ID")
        if not req_id or not isinstance(req_id, str) or len(req_id.strip()) == 0:
            req_id = str(uuid4())
        request.state.request_id = req_id

        response = await call_next(request)
        response.headers["X-Request-ID"] = req_id

        duration_ms = round((time.perf_counter() - start_time) * 1000.0, 2)
        # Safe structured logging without logging PII, Auth headers, or raw payloads
        logger.info(
            f"service={settings.APP_NAME} request_id={req_id} method={request.method} "
            f"route={request.url.path} status_code={response.status_code} duration_ms={duration_ms}ms"
        )
        return response

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
    app.include_router(processing_router, prefix="/api/v1")
    app.include_router(advanced_router, prefix="/api/v1")
    app.include_router(ml_router)

    return app


app = create_app()
