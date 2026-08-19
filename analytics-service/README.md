# CommunityOTT Python Analytics Service

[![FastAPI](https://img.shields.io/badge/FastAPI-0.111%2B-brightgreen.svg)](https://fastapi.tiangolo.com/)
[![Python](https://img.shields.io/badge/Python-3.12%2B-blue.svg)](https://www.python.org/)
[![Pydantic](https://img.shields.io/badge/Pydantic-v2-orange.svg)](https://docs.pydantic.dev/)
[![Docker](https://img.shields.io/badge/Docker-Containerized-blue.svg)](Dockerfile)
[![Contract](https://img.shields.io/badge/Contract-analytics--contract--v1-purple.svg)](../Docs/ANALYTICS_CONTRACT_V1.md)

The **CommunityOTT Analytics Service** is an independent, lightweight Python/FastAPI microservice designed to handle advanced statistical computation, content recommendation modeling, and predictive analytics for the CommunityOTT ecosystem.

---

## 🏛️ Architectural Boundary & Separation of Concerns

```
    Spring Boot Monolith (Producer)
       • Database Owner (PostgreSQL 16)
       • Business Rules & RBAC
       • Video Processing & Transcoding
       • Analytics Daily Aggregation
                      │
                      │ HTTP GET /api/v1/analytics/export
                      │ (analytics-contract-v1)
                      ▼
    Python Analytics Service (Consumer)
       • AnalyticsClient (httpx async)
       • Pydantic contract validation & error handling
       • Multi-page pagination with infinite-loop safeguards
       • Transient retry policies (503/timeouts)
       • Zero direct PostgreSQL / Redis / MinIO access
       • Zero PII handling
```

### 🔒 Strict Security & Data Access Rules
1. **Zero Database Access:** The Python service does **NOT** connect directly to the operational PostgreSQL database. It never accesses users, passwords, sessions, or raw database tables.
2. **Zero Storage / Cache Access:** The service does not connect directly to Redis or MinIO.
3. **Contract-Driven:** All analytics ingestion operates strictly through the versioned [`analytics-contract-v1`](../Docs/ANALYTICS_CONTRACT_V1.md) export endpoint provided by the Spring Boot backend (`GET /api/v1/analytics/export`).
4. **Zero PII Exposure:** The service never ingests or stores user IDs, IP addresses, tokens, emails, phone numbers, or device fingerprints.

---

## 📦 Project Structure

```
analytics-service/
├── app/
│   ├── __init__.py
│   ├── main.py              # Application factory, lifespan, CORS, and routing
│   ├── clients/
│   │   ├── __init__.py
│   │   ├── analytics_client.py # Async HTTP Client for analytics-contract-v1
│   │   └── errors.py        # Dedicated client & contract exceptions
│   ├── core/
│   │   ├── __init__.py
│   │   ├── config.py        # Pydantic Settings configuration management
│   │   ├── logging.py       # Structured JSON logging (zero credential leakage)
│   │   └── errors.py        # Centralized JSON exception handlers
│   ├── api/
│   │   ├── __init__.py
│   │   └── routes/
│   │       ├── __init__.py
│   │       ├── health.py    # Healthcheck endpoint (GET /api/v1/analytics/health)
│   │       ├── metadata.py  # Service metadata & contract declaration
│   │       └── data.py      # Internal data verification endpoint (GET /api/v1/analytics/data)
│   └── schemas/
│       ├── __init__.py
│       ├── metadata.py      # Pydantic response models (Health, Metadata)
│       └── contract.py      # Strongly-typed schemas for analytics-contract-v1
├── tests/
│   ├── __init__.py
│   ├── test_analytics_client.py # Comprehensive unit tests for AnalyticsClient (20+ tests)
│   ├── test_data_endpoint.py    # Tests for /api/v1/analytics/data endpoint
│   ├── test_health.py           # Health endpoint tests
│   ├── test_metadata.py         # Metadata and contract declaration tests
│   ├── test_errors.py           # Error handling, 404, CORS, and OpenAPI tests
│   └── test_security.py         # Security boundary tests (zero secrets leakage)
├── Dockerfile               # Production multi-stage Python 3.12-slim container
├── .dockerignore
├── .env.example             # Safe environment variable template
├── requirements.txt         # Pinned lightweight dependencies
├── pyproject.toml
└── README.md
```

---

## ⚙️ Environment Variables

Configuration is loaded from environment variables or a local `.env` file via `Pydantic Settings`:

| Variable | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `APP_NAME` | `string` | `communityott-analytics` | Service identifier |
| `APP_VERSION` | `string` | `1.0.0` | Semantic version |
| `ENVIRONMENT` | `string` | `local` | Runtime environment (`local`, `dev`, `staging`, `prod`) |
| `LOG_LEVEL` | `string` | `INFO` | Logging level (`DEBUG`, `INFO`, `WARNING`, `ERROR`) |
| `HOST` | `string` | `0.0.0.0` | Host binding address |
| `PORT` | `int` | `8001` | Port binding number |
| `ALLOWED_ORIGINS` | `string` | `http://localhost:3000,...` | Comma-separated CORS origins |
| `SPRING_BOOT_BASE_URL`| `string` | `http://localhost:8080`| Upstream Spring Boot backend URL |
| `ANALYTICS_EXPORT_PATH`| `string` | `/api/v1/analytics/export`| Export endpoint path on Spring Boot |
| `ANALYTICS_CONTRACT_VERSION`| `string` | `analytics-contract-v1`| Target contract version |
| `HTTP_TIMEOUT_SECONDS`| `float` | `10.0` | HTTP request timeout in seconds |
| `HTTP_MAX_RETRIES` | `int` | `2` | Max retry attempts on 503/timeout |

---

## 🚀 Running Locally

### 1. Create Virtual Environment & Install Dependencies
```bash
cd analytics-service
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

### 2. Start Application with Uvicorn
```bash
uvicorn app.main:app --reload --port 8001
```

* **Health Endpoint:** `http://localhost:8001/api/v1/analytics/health`
* **Metadata Endpoint:** `http://localhost:8001/api/v1/analytics/metadata`
* **Data Verification Endpoint:** `http://localhost:8001/api/v1/analytics/data`
* **Interactive Swagger UI:** `http://localhost:8001/docs`

---

## 🧪 Testing

Run pytest with async test discovery across all 33 unit and API test suites:

```bash
cd analytics-service
pytest -v
```

---

## 🔮 Roadmap & Phases
* **Phase 7.1 (Complete):** Python project structure, FastAPI application factory, Health/Metadata APIs, structured logging, Dockerization.
* **Phase 7.2 (Complete):** Analytics Contract Client (consuming `GET /api/v1/analytics/export` from Spring Boot with Pydantic validation, pagination, retry policy, and error mapping).
* **Phase 7.3+:** Advanced statistical computations, aggregation summaries, and recommendation model pipelines.
