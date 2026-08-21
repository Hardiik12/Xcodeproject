# CommunityOTT Python Analytics Service

[![FastAPI](https://img.shields.io/badge/FastAPI-0.111%2B-brightgreen.svg)](https://fastapi.tiangolo.com/)
[![Python](https://img.shields.io/badge/Python-3.12%2B-blue.svg)](https://www.python.org/)
[![Pandas](https://img.shields.io/badge/Pandas-2.2%2B-150458.svg)](https://pandas.pydata.org/)
[![NumPy](https://img.shields.io/badge/NumPy-1.26%2B-013243.svg)](https://numpy.org/)
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
    Python Analytics Service (Consumer & Processor)
       • AnalyticsClient (httpx async)
       • Pydantic contract validation & error handling
       • Processing Layer (Pandas DataFrame & NumPy)
       • Deterministic Aggregations & Summary Statistics
       • Zero direct PostgreSQL / Redis / MinIO access
       • Zero PII handling
```

### 🔒 Strict Security & Data Access Rules
1. **Zero Database Access:** The Python service does **NOT** connect directly to the operational PostgreSQL database. It never accesses users, passwords, sessions, or raw database tables.
2. **Zero Storage / Cache Access:** The service does not connect directly to Redis or MinIO.
3. **Contract-Driven:** All analytics ingestion operates strictly through the versioned [`analytics-contract-v1`](../Docs/ANALYTICS_CONTRACT_V1.md) export endpoint provided by the Spring Boot backend (`GET /api/v1/analytics/export`).
4. **Zero PII Exposure:** The service never ingests, processes, or stores user IDs, IP addresses, tokens, emails, phone numbers, or device fingerprints. Any presence of sensitive fields triggers a fatal `SensitiveDataError`.

---

## 🧮 Phase 7.3 — Data Processing Layer

### 1. Pandas & NumPy Responsibilities
- **Pandas (`pandas>=2.2.0`):** Used strictly for structured tabular manipulation, schema validation, grouping, and deterministic multi-dimensional aggregations (by content, category, language, platform, and date).
- **NumPy (`numpy>=1.26.0`):** Used for vectorized numerical calculations, safe floating-point division, and statistical distribution computations.
- **Machine Learning Boundary:** Machine learning frameworks (scikit-learn, TensorFlow, PyTorch, XGBoost, LightGBM) are **NOT** present in Phase 7.3. All calculations are exact and deterministic.

### 2. DataFrame Schema & Types

| Column Name | Pandas Dtype | Invariant / Constraints |
| :--- | :--- | :--- |
| `date` | `datetime64[ns]` (UTC) | Valid ISO date in UTC |
| `content_id` | `int64` | Positive integer (`>= 1`) |
| `category_id` | `Int64` (nullable) | Positive integer or `NA` |
| `language_id` | `Int64` (nullable) | Positive integer or `NA` |
| `platform` | `string` | Uppercase: `IOS`, `ANDROID`, `WEB` |
| `sessions` | `int64` | `sessions >= 0` |
| `plays` | `int64` | `plays >= 0` |
| `unique_viewers` | `int64` | `unique_viewers >= 0` |
| `watch_time_seconds`| `int64` | `watch_time_seconds >= 0` |
| `completed_plays` | `int64` | `0 <= completed_plays <= plays` |
| `completion_rate` | `float64` | `0.0 <= completion_rate <= 1.0` |
| `buffering_events` | `int64` | `buffering_events >= 0` |
| `playback_errors` | `int64` | `playback_errors >= 0` |
| `quality_changes` | `int64` | `quality_changes >= 0` |

### 3. Aggregation & Recalculation Formulas
When aggregating across grain dimensions (e.g. content, category, platform), the completion rate is **never averaged**. It is dynamically recomputed from aggregated sums:

$$\text{completion\_rate} = \begin{cases} \text{round}\left(\frac{\sum \text{completed\_plays}}{\sum \text{plays}}, 4\right) & \text{if } \sum \text{plays} > 0 \\ 0.0 & \text{if } \sum \text{plays} = 0 \end{cases}$$

### 4. Summary Statistics Convention
For metric statistics (`sessions`, `plays`, `unique_viewers`, `watch_time_seconds`, `completed_plays`, `completion_rate`):
- **Sample Standard Deviation:** Computed using NumPy with degree of freedom $ddof=1$.
- **Small Sample Guard:** When sample size $N \le 1$, standard deviation evaluates to $0.0$.
- **Quantiles & Measures:** Count, Mean, Median, Min, Max, and Sample Standard Deviation rounded to 4 decimal places.

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
│   ├── processing/
│   │   ├── __init__.py
│   │   ├── dataframe_builder.py # Strongly-typed DataFrame construction
│   │   ├── data_cleaner.py      # Controlled cleaning & deduplication
│   │   ├── data_validator.py    # Invariants, bounds, and PII inspection
│   │   ├── aggregations.py      # Content, platform, category, date aggregations
│   │   ├── statistics.py        # NumPy summary statistics & content rankings
│   │   └── errors.py            # Processing exceptions (DataValidationError, etc.)
│   ├── services/
│   │   ├── __init__.py
│   │   └── analytics_processing_service.py # Ingestion & processing orchestrator
│   ├── api/
│   │   ├── __init__.py
│   │   └── routes/
│   │       ├── __init__.py
│   │       ├── health.py    # Healthcheck endpoint (GET /api/v1/analytics/health)
│   │       ├── metadata.py  # Service metadata & contract declaration
│   │       ├── data.py      # Internal data verification endpoint (GET /api/v1/analytics/data)
│   │       └── processing.py# Processing APIs (Summary, Content, Platforms, etc.)
│   └── schemas/
│       ├── __init__.py
│       ├── metadata.py      # Response models for health and metadata
│       ├── contract.py      # Strongly-typed schemas for analytics-contract-v1
│       └── processing.py    # Response models for processing and aggregations
├── tests/
│   ├── __init__.py
│   ├── test_analytics_client.py # Unit tests for AnalyticsClient (20+ tests)
│   ├── test_processing.py       # Processing layer tests (DataFrame, aggregations, stats, PII)
│   ├── test_processing_api.py   # API route tests for processing endpoints
│   ├── test_data_endpoint.py    # Tests for /api/v1/analytics/data endpoint
│   ├── test_health.py           # Health endpoint tests
│   ├── test_metadata.py         # Metadata and contract declaration tests
│   ├── test_errors.py           # Error handling, 404, CORS, and OpenAPI tests
│   └── test_security.py         # Security boundary tests (zero secrets leakage)
├── Dockerfile               # Production multi-stage Python 3.12-slim container
├── .dockerignore
├── .env.example             # Safe environment variable template
├── requirements.txt         # Pinned lightweight dependencies (FastAPI, Pandas, NumPy)
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
* **Summary Statistics:** `http://localhost:8001/api/v1/analytics/processing/summary`
* **Content Rankings:** `http://localhost:8001/api/v1/analytics/processing/content?by=plays&limit=10`
* **Platform Breakdown:** `http://localhost:8001/api/v1/analytics/processing/platforms`
* **Interactive Swagger UI:** `http://localhost:8001/docs`

---

## 📈 Phase 7.4 — Advanced Statistical & Business Analytics

### 1. Analytics Responsibilities
- **Engagement Analytics:** Overall completion rate, average watch times (per play/session), viewer volume ratios, buffering rate, playback error rate, and adaptive rendition change rate.
- **Content Performance Scoring:** Min-max normalized composite scoring across plays, watch time, unique viewers, and completion rate ($0.0 \text{ to } 100.0$) with deterministic tie-breaking.
- **Period-over-Period Growth:** Metric growth calculations with directional classification (`UP`, `DOWN`, `FLAT`).
- **Daily Time-Series Trends:** Chronological day-over-day tracking across all playback dimensions.
- **Platform & Catalog Breakdown:** Market share distribution analysis across `IOS`, `ANDROID`, `WEB`, categories, and languages (handling unassigned dimensions cleanly).
- **Statistical Distributions:** Comprehensive measures (Mean, Median, Min, Max, Sample Std Dev with $ddof=1$, $P_{25}, P_{50}, P_{75}, P_{90}, P_{95}$).
- **Statistical Outlier Detection:** Interquartile Range ($IQR$) outlier identification with `HIGH` / `MEDIUM` severity classification.
- **Actionable Health Heuristics:** Rule-based evaluation of catalog and streaming performance (`HIGH_ENGAGEMENT`, `LOW_COMPLETION`, `HIGH_BUFFERING`, `HIGH_ERROR_RATE`, `RAPID_GROWTH`, `DECLINING_CONTENT`).

### 2. Advanced Analytics REST API Endpoints (`/api/v1/analytics/advanced/*`)

| Endpoint | Method | Output / Description |
| :--- | :--- | :--- |
| `/engagement` | `GET` | Viewer retention, watch time ratios, player reliability rates |
| `/content` | `GET` | Ranked content performance scores ($1 \le N \le 100$) |
| `/growth` | `GET` | Period-over-period comparative metrics and trends |
| `/trends` | `GET` | Chronological daily summaries and DoD growth |
| `/platforms` | `GET` | Cross-platform performance & market share ratios |
| `/categories` | `GET` | Catalog category viewing and watch time shares |
| `/languages` | `GET` | Content language viewing and watch time shares |
| `/distributions` | `GET` | Continuous metric distributions and percentiles (P25-P95) |
| `/anomalies` | `GET` | Statistical IQR outlier detection |
| `/insights` | `GET` | Actionable heuristic health alerts |

---

## 🤖 Phase 7.5 — Machine Learning Foundation & Feature Engineering

### 1. Feature Engineering Architecture
- **Base Analytical Grain:** `content_id + date + platform` (strictly aggregate; zero user-level rows).
- **Feature Version:** `features-v1`
- **Engagement Features:** Ratios computed with zero-safe division (`plays_per_session`, `plays_per_viewer`, `watch_time_per_play`, `watch_time_per_session`, `completed_plays_ratio`, `buffering_rate`, `error_rate`, `quality_change_rate`).
- **Temporal Features (UTC):** `day_of_week`, `day_of_month`, `day_of_year`, `week_of_year`, `month`, `quarter`, `is_weekend`.
- **Content Share Features:** Relative daily market shares (`content_play_share`, `content_watch_time_share`, `content_viewer_share`).
- **Historical Lag & Rolling Features:** 1-day lag metrics and 7-day cumulative historical rolling sums partitioned by `(content_id, platform)` without future lookahead.
- **Growth Ratios:** Day-over-day relative ratios (`plays_growth_1d`, `watch_time_growth_1d`, `viewer_growth_1d`).
- **Supervised Target Construction:** Shift(-1) next-day targets strictly isolated from input feature matrices.

### 2. Preprocessing & Leakage Protection
- **Chronological Split:** 70% Train, 15% Validation, 15% Test strictly ordered by calendar date ($\max(\text{Train}) < \min(\text{Val}) \le \max(\text{Val}) < \min(\text{Test})$).
- **Scikit-Learn ColumnTransformer:** Fitted **only on the training split**. Validation and test sets are transformed using fitted training statistics.
- **Imputation & Encoding:** `SimpleImputer(strategy="median")` + `StandardScaler` for numerics; `SimpleImputer(strategy="most_frequent")` + `OneHotEncoder(handle_unknown="ignore")` for categoricals.
- **Metadata Endpoint:** `GET /api/v1/ml/features/metadata` returns schema metadata and feature registry definitions.

> [!NOTE]
> Phase 7.5 establishes ML-ready feature datasets and preprocessing pipelines but **does not implement production model training or inference**.

---

## 🧪 Testing

Run pytest across all 139 unit, processing, advanced analytics, and ML test suites:

```bash
cd analytics-service
pytest -v
```

---

## 🔮 Roadmap & Phases
* **Phase 7.1 (Complete):** Python project structure, FastAPI application factory, Health/Metadata APIs, structured logging, Dockerization.
* **Phase 7.2 (Complete):** Analytics Contract Client (consuming `GET /api/v1/analytics/export` from Spring Boot with Pydantic validation, pagination, retry policy, and error mapping).
* **Phase 7.3 (Complete):** Tabular analytics processing with Pandas and NumPy (DataFrame schemas, invariant validation, PII rejection, deterministic aggregations, sample statistics, content rankings, and processing API endpoints).
* **Phase 7.4 (Complete):** Advanced Statistical & Business Analytics (Engagement metrics, performance scoring, period growth, daily trends, market shares, percentile distributions, IQR anomaly detection, actionable health insights, and 10 REST endpoints).
* **Phase 7.5 (Complete):** Machine Learning Foundation & Feature Engineering (`features-v1`, lag/rolling 7d metrics, temporal UTC features, chronological splitting, scikit-learn preprocessing pipeline, metadata registry).
* **Phase 7.6+:** Content recommendation system and predictive inference modeling.
