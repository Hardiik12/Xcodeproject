"""Service metadata and contract declaration endpoints."""

from fastapi import APIRouter, Depends
from app.core.config import Settings, get_settings
from app.schemas.metadata import MetadataResponse, ContractDeclaration

router = APIRouter(prefix="/analytics", tags=["Metadata & Contract"])


@router.get(
    "/metadata",
    response_model=MetadataResponse,
    summary="Service Metadata",
    description="Returns public service metadata including agreed data contract version and deployment environment.",
)
async def get_metadata(settings: Settings = Depends(get_settings)) -> MetadataResponse:
    """Expose safe metadata without leaking internal credentials or environment secrets."""
    return MetadataResponse(
        service=settings.APP_NAME,
        version=settings.APP_VERSION,
        contract_version=settings.ANALYTICS_CONTRACT_VERSION,
        environment=settings.ENVIRONMENT,
    )


@router.get(
    "/contract",
    response_model=ContractDeclaration,
    summary="Contract Declaration",
    description="Returns the integration relationship and contract version with the Spring Boot backend.",
)
async def get_contract_declaration(settings: Settings = Depends(get_settings)) -> ContractDeclaration:
    """Declare contract boundary relationship."""
    return ContractDeclaration(
        contract_version=settings.ANALYTICS_CONTRACT_VERSION,
        producer="Spring Boot Monolith",
        consumer="Python Analytics Service",
        status="FOUNDATION_READY",
    )
