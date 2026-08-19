# CommunityOTT — Modern Cultural OTT Platform

[![Platform](https://img.shields.io/badge/Platform-iOS%2017%2B%20%7C%20Spring%20Boot%203.3.2-blue.svg)](https://github.com/Hardiik12/Xcodeproject)
[![Java](https://img.shields.io/badge/Java-21%20LTS-orange.svg)](https://openjdk.org/)
[![Swift](https://img.shields.io/badge/Swift-5.10%20%7C%20SwiftUI-red.svg)](https://developer.apple.com/swift/)
[![Database](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Cache](https://img.shields.io/badge/Redis-7-red.svg)](https://redis.io/)
[![Storage](https://img.shields.io/badge/MinIO-S3%20Compatible-green.svg)](https://min.io/)
[![Tests](https://img.shields.io/badge/Automated%20Tests-424%20Passing-brightgreen.svg)](https://github.com/Hardiik12/Xcodeproject)

CommunityOTT is a production-grade, community-rooted Over-The-Top (OTT) streaming platform designed for cultural preservation, regional cinema, documentaries, educational programming, podcasts, and community stories.

Built as a monolithic architecture pairing a native iOS (SwiftUI) frontend with an enterprise Spring Boot backend, the platform delivers high-performance adaptive bitrate (HLS) streaming, real-time telemetry, automated analytics aggregation, and fine-grained RBAC security.

---

## 🏛️ System Architecture

```
                                  COMMUNITYOTT ECOSYSTEM
                                             │
             ┌───────────────────────────────┴───────────────────────────────┐
             ▼                                                               ▼
    Native iOS Client                                              Backend Monolith
    (Swift / SwiftUI / AVKit)                                    (Java 21 / Spring Boot 3.3)
             │                                                               │
             │ REST API / JWT / Bearer                                       ├─ REST API & OpenAPI
             ├───────────────────────────────────────────────────────────────┤  (Swagger / Actuator)
             │                                                               │
             │ Signed Token Playback URL                                     ├─ Spring Security & RBAC
             └─────────────────────────────────────────┐                     │  (SUPER_ADMIN, MANAGER, USER)
                                                       │                     │
                                                       ▼                     ├─ Media Pipeline
                                                MinIO S3 / CDN               │  (FFmpeg Transcoding & HLS)
                                                (HLS .m3u8 / .ts)            │
                                                       │                     ├─ Telemetry & Aggregation
                                                       │                     │  (Event Ingestion & Checkpointing)
                                                       │                     │
                                      ┌────────────────┴──────────────┐      ├─ PostgreSQL 16
                                      ▼                               ▼      │  (Flyway V1 - V18)
                                PostgreSQL 16                      Redis 7   │
                                (Primary Relational)            (Cache / Lock)
```

---

## 📱 iOS Application (`CommunityOTT/`)

The native iOS client is built with **SwiftUI** and **AVKit**, following a clean **MVVM + Repository** architecture with zero third-party UI dependencies.

### Key iOS Features
* **Cinematic Dark Theme:** Custom palette tailored for community media (`#0B0B0F` background, `#D6A84F` primary gold accent, `#641F2B` secondary burgundy).
* **Adaptive Video Player:** AVKit/AVFoundation player supporting HLS adaptive bitrates, tokenized secure playback, picture-in-picture, subtitle tracks, and background audio for podcasts.
* **Watch Progress & History:** Real-time heartbeats and automatic resume synchronization with the backend continue-watching system.
* **Discovery & Search:** Category carousel browsing, multi-criteria filtering (language, genre, featured status), and debounced search.
* **Saved Content / My List:** Bookmark and offline-ready playlist management.
* **Profile & Authentication:** Mobile OTP and JWT session management with auto-refreshing tokens.

---

## ☕ Monolithic Backend (`backend/`)

The backend is built with **Spring Boot 3.3.2** on **Java 21**, running in local Docker or AWS production cloud (RDS PostgreSQL, ElastiCache Redis, S3/CloudFront).

### Backend Modules & Architecture
| Module | Responsibilities | Key Technologies |
| :--- | :--- | :--- |
| **Authentication & Users** | Mobile OTP verification, JWT access/refresh tokens, session lifecycles | Spring Security, JJWT, PostgreSQL |
| **RBAC Authorization** | Role hierarchy (`SUPER_ADMIN`, `MANAGER`, `CREATOR`, `USER`) and dynamic permission evaluation | Spring Security `@PreAuthorize`, Redis cache |
| **Content & Categories** | Content catalog, movie/series/podcast metadata, localized multilingual tagging | Spring Data JPA, Hibernate, PostgreSQL |
| **Video Ingestion & Processing** | Asynchronous video upload, FFprobe inspection, thumbnail generation | MinIO S3 SDK, ProcessBuilder |
| **Transcoding & Packaging** | Multi-resolution FFmpeg transcoding (1080p, 720p, 480p, 360p) and HLS master manifest packaging | FFmpeg, Apple HLS specification |
| **Secure Media Delivery** | Tokenized signed media URLs, CDN-ready signed token verification, DRM-ready headers | HMAC-SHA256, MinIO / CDN |
| **Playback & Telemetry** | Session tracking, watch progress checkpoints, continue watching, telemetry ingestion | PostgreSQL, Redis, REST pipeline |
| **Analytics Aggregation** | Cron-driven incremental daily metrics aggregation, idempotent checkpoints, Redis distributed locks | Spring Task Scheduling, PostgreSQL |
| **Manager / Admin Analytics** | Comprehensive KPI dashboards, period-over-period trend analysis, category/platform summaries | JPA Projections, Redis caching |
| **Analytics Export** | Versioned data contract (`analytics-contract-v1`) for external analytics/ML ingestion | Spring Data Pagination, Zero-PII Guarantee |

---

## 🚀 Getting Started (Local Development)

### Prerequisites
* **macOS** with Xcode 15+ (for iOS development)
* **Java 21 LTS** (`openjdk@21`)
* **Docker Desktop** (for PostgreSQL, Redis, and MinIO)
* **FFmpeg** (`brew install ffmpeg`)

---

### Step 1: Start Infrastructure Containers

```bash
cd backend
docker compose up -d
```

Verify that all three services are running:
* **PostgreSQL 16**: `localhost:5432` (`communityott` / `communityott_password`)
* **Redis 7**: `localhost:6379`
* **MinIO Storage**: `http://localhost:9000` (API) & `http://localhost:9001` (Web Console)

---

### Step 2: Run Backend Monolith

```bash
cd backend
./mvnw spring-boot:run
```

The server will start on port `8080` with automatic Flyway database migrations (V1 through V18).

* **Interactive OpenAPI Swagger UI:** [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
* **OpenAPI 3 JSON Spec:** [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

---

### Step 3: Launch iOS App

1. Open `CommunityOTT.xcodeproj` in **Xcode**.
2. Select any iOS Simulator (e.g. `iPhone 16 Pro`).
3. Press `Cmd + R` to build and run.

---

## 🧪 Testing & Verification

The backend includes a comprehensive test suite covering unit, integration, and security test cases.

```bash
cd backend
./mvnw clean test
```

```
[INFO] Results:
[INFO] Tests run: 424, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## 📚 Documentation & Specifications

Detailed technical specifications and architectural walkthroughs are available in the [`Docs/`](Docs/) directory:

* [`Docs/ANALYTICS_CONTRACT_V1.md`](Docs/ANALYTICS_CONTRACT_V1.md) — Versioned Analytics Data Contract specification (`analytics-contract-v1`).
* [`Docs/backend-api-contract.md`](Docs/backend-api-contract.md) — REST API specifications and response standards.
* [`Docs/PHASE_5_1_WALKTHROUGH.md` - `PHASE_6_7_WALKTHROUGH.md`](Docs/) — Detailed engineering walkthroughs for each backend milestone.

---

## 📄 License & Intellectual Property

Copyright © 2026 CommunityOTT. All rights reserved.
