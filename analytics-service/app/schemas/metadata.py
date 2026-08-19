"""Pydantic schemas for health, metadata, and contract declaration."""

from pydantic import BaseModel, Field


class HealthResponse(BaseModel):
    """Health check response payload."""
    success: bool = Field(default=True, description="Health status indicator")
    service: str = Field(description="Name of the service")
    status: str = Field(default="UP", description="Operational state")
    version: str = Field(description="Semantic version of the application")


class MetadataResponse(BaseModel):
    """Service metadata response payload exposing contract and environment details."""
    service: str = Field(description="Name of the service")
    version: str = Field(description="Semantic version of the application")
    contract_version: str = Field(description="Agreed data contract version with Spring Boot")
    environment: str = Field(description="Current deployment environment")


class ContractDeclaration(BaseModel):
    """Declaration of the contract relationship between Spring Boot and Python."""
    contract_version: str = Field(default="analytics-contract-v1", description="Contract name and version")
    producer: str = Field(default="Spring Boot Monolith", description="Service producing the data contract")
    consumer: str = Field(default="Python Analytics Service", description="Service consuming the contract")
    status: str = Field(default="FOUNDATION_READY", description="Contract integration readiness status")
