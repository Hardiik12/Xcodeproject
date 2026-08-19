# CommunityOTT — Spring Boot Backend Monolith

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.2-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21%20LTS-orange.svg)](https://openjdk.org/)
[![Database](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Migrations](https://img.shields.io/badge/Flyway-V1%20through%20V18-red.svg)](https://flywaydb.org/)
[![Cache](https://img.shields.io/badge/Redis-7%20AOF-red.svg)](https://redis.io/)
[![Storage](https://img.shields.io/badge/MinIO-S3%20Compatible-green.svg)](https://min.io/)
[![Tests](https://img.shields.io/badge/Tests-424%20Passing-brightgreen.svg)](pom.xml)

CommunityOTT Backend is a modular monolith powering the CommunityOTT streaming platform. Built with **Java 21** and **Spring Boot 3.3.2**, it provides authentication, content management, video transcoding, adaptive HLS delivery, real-time playback telemetry, analytics aggregation, and admin/manager reporting dashboards.

---

## 🏗️ Architecture & Component Stack

```
                              CLIENT APPS
                   (iOS App / Admin Web / API Clients)
                                  │
                                  ▼
                   Spring Boot 3.3.2 Monolith (:8080)
 ┌────────────────────────────────┬────────────────────────────────┐
 │           SECURITY             │            CONTENT             │
 │  • Spring Security / JJWT      │  • Content Catalog (JPA)       │
 │  • Dynamic RBAC Authorization  │  • Categories & Multilingual   │
 │  • Rate Limiting & Auth Audit  │  • Search & Filtering          │
 ├────────────────────────────────┼────────────────────────────────┤
 │        MEDIA PIPELINE          │           PLAYBACK             │
 │  • MinIO Object Storage        │  • Playback Sessions & Progress│
 │  • FFmpeg Multi-Bitrate (HLS)  │  • Watch History & Resume      │
 │  • Signed Token Media Delivery │  • My List / Saved Content     │
 ├────────────────────────────────┼────────────────────────────────┤
 │           TELEMETRY            │      ANALYTICS & REPORTING     │
 │  • Playback Event Ingestion    │  • Daily Aggregation Cron      │
 │  • Buffer / Error Tracking     │  • Manager & Admin Dashboards  │
 │  • Heartbeat State Machine     │  • Python Contract v1 Export   │
 └────────────────────────────────┴────────────────────────────────┘
                                  │
       ┌──────────────────────────┼──────────────────────────┐
       ▼                          ▼                          ▼
 PostgreSQL 16                 Redis 7                    MinIO / S3
(Primary Database)         (Cache / Locks)             (Media Storage)
```

---

## 📦 Project Structure

```
backend/
├── src/main/java/com/communityott/
│   ├── analytics/          # Daily aggregation, Manager/Admin analytics, Contract v1 export
│   │   ├── controller/     # AnalyticsController, ManagerAnalyticsController, AdminAnalyticsController
│   │   ├── dto/            # Metrics DTOs, PeriodComparisonDto, AnalyticsExportRecordDto
│   │   ├── entity/         # AnalyticsDailyMetric, AnalyticsAggregationCheckpoint
│   │   ├── repository/     # AnalyticsDailyMetricRepository, CheckpointRepository
│   │   └── service/        # AnalyticsAggregationService, AnalyticsQueryService, AnalyticsExportService
│   ├── auth/               # Mobile OTP, JWT token issuance, session authentication
│   ├── common/             # Response wrappers, global exception handling, Redis/MinIO configs
│   ├── content/            # Movies, series, episodes, categories, languages, tags
│   ├── media/              # Video upload, FFmpeg transcoding, HLS packaging, signed URL delivery
│   ├── playback/           # Playback sessions, watch progress, continue watching, history
│   ├── role/               # Roles, permissions, role-permission mappings
│   ├── saved/              # My List / Saved content operations
│   ├── telemetry/          # High-throughput event ingestion (play, pause, seek, buffer, error)
│   └── user/               # User profiles, user roles, user management
├── src/main/resources/
│   ├── db/migration/       # Flyway SQL migrations (V1__init_schema.sql to V18__analytics_schema.sql)
│   └── application.yml     # Application configuration & profiles (local, prod)
└── docker-compose.yml       # Local infrastructure setup (Postgres, Redis, MinIO)
```

---

## ⚙️ Development Environment

### 1. Prerequisites
* **Java 21 JDK** (`export JAVA_HOME=$(/usr/libexec/java_home -v 21)`)
* **Docker Desktop**
* **FFmpeg / FFprobe** (`brew install ffmpeg`)

### 2. Start Supporting Infrastructure
```bash
# From backend directory
docker compose up -d
```

| Service | Container Name | Port | Credentials | Purpose |
| :--- | :--- | :--- | :--- | :--- |
| **PostgreSQL 16** | `communityott-postgres` | `5432` | `communityott` / `communityott_password` | Primary Relational DB |
| **Redis 7** | `communityott-redis` | `6379` | *None* | Cache, Session Lock & Tokens |
| **MinIO API** | `communityott-minio` | `9000` | `communityott` / `communityott_minio_password` | S3-Compatible Object Store |
| **MinIO Console** | `communityott-minio` | `9001` | `communityott` / `communityott_minio_password` | Web Storage Browser |

---

## 🚀 Running the Application

```bash
./mvnw spring-boot:run
```

Once started:
* **Base URL:** `http://localhost:8080`
* **Swagger UI:** `http://localhost:8080/swagger-ui/index.html`
* **OpenAPI 3 Docs:** `http://localhost:8080/v3/api-docs`
* **Health Check:** `http://localhost:8080/actuator/health`

---

## 🔐 Authentication & RBAC

The backend uses **stateless JWT Bearer tokens** combined with **fine-grained role-based permissions**:

### Standard Roles
* **`SUPER_ADMIN`**: Full platform control, system settings, global metrics.
* **`MANAGER`**: Content catalog management, business KPI reporting (`ANALYTICS_VIEW`).
* **`CREATOR`**: Video ingestion, media metadata authoring.
* **`USER`**: Playback, profile management, watch progress, My List.

### Key API Endpoints
```
# Authentication
POST /api/v1/auth/otp/request            Request mobile OTP
POST /api/v1/auth/otp/verify             Verify OTP & issue JWT tokens
POST /api/v1/auth/token/refresh          Refresh expired access token

# Playback & Telemetry
POST /api/v1/playback/sessions/start     Start playback session
POST /api/v1/playback/progress           Sync watch progress heartbeat
POST /api/v1/telemetry/events            Ingest playback events (buffer, seek, error)

# Content & Delivery
GET  /api/v1/content                     List & filter content catalog
GET  /api/v1/media/delivery/{contentId}  Generate signed tokenized HLS stream URL

# Manager & Admin Analytics
GET  /api/v1/manager/analytics/overview  Manager KPI summary with period comparisons
GET  /api/v1/manager/analytics/trends    Daily time-series metrics
GET  /api/v1/admin/analytics/system      System health, error rates, platform distribution
GET  /api/v1/analytics/export            Versioned Data Contract v1 export (Zero PII)
```

---

## 📊 Analytics Data Contract (`analytics-contract-v1`)

For data science, business intelligence, and future Python/FastAPI consumption, the backend exposes:
```
GET /api/v1/analytics/export?from=YYYY-MM-DD&to=YYYY-MM-DD&platform=IOS&page=0&size=100
```
* **Contract Specification:** Full details in [`Docs/ANALYTICS_CONTRACT_V1.md`](../Docs/ANALYTICS_CONTRACT_V1.md).
* **Privacy Guarantee:** Strict Zero-PII enforcement (no user IDs, IPs, tokens, or device hashes).
* **Guaranteed Precision:** 4-decimal completion rate calculations with zero NaN/Infinity edge cases.

---

## 🧪 Testing

Run the automated test suite against the local Docker containers:

```bash
./mvnw clean test
```

### Test Coverage Highlights
* **Authentication:** OTP flows, token validation, expiration, and replay prevention.
* **RBAC:** Multi-role `@PreAuthorize` authorization boundaries and forbidden access checks.
* **Playback Pipeline:** Session tracking, watch progress calculation, continue-watching filters.
* **Media & Delivery:** Tokenized HLS URL generation and HMAC signature validation.
* **Analytics Engine:** Incremental daily aggregation, Redis distributed locking, and export contract serialization.

**Test Results:** **424 passing tests, 0 failures, 0 errors**.

---

## 📄 License

Copyright © 2026 CommunityOTT. All rights reserved.
