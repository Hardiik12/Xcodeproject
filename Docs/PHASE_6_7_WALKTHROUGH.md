# Phase 6.7 Walkthrough: Analytics API Hardening & Data Contract

## 1. Objective
The primary objective of **Phase 6.7** is to harden the Analytics Aggregation APIs established in Phase 6.6 into a production-grade, highly dependable, well-documented, and strictly typed data contract. This contract serves as the foundation for:
1. **iOS Frontend** (SwiftUI views, analytics telemetry dashboards, and engagement charts)
2. **Android Frontend** (Jetpack Compose analytics charts)
3. **Future Manager Frontend** (Operational content analytics & performance views)
4. **Future Admin Frontend** (Platform-wide health and metric oversight)
5. **Future Python / FastAPI Analytics Service** (ML recommendations, forecasting, cohort retention, and churn prediction)

---

## 2. Existing Phase 6.6 Architecture Preservation
Phase 6.7 preserves 100% of the Phase 6.6 foundational architecture:
- **Flyway Migration `V18`**: Unmodified schema for `analytics_daily_metrics` and `analytics_aggregation_checkpoint`.
- **Incremental Processing**: `AnalyticsAggregationService` high-water mark cursor tracking based on `last_processed_event_id`.
- **Idempotency & Concurrency**: Redis distributed lock (`communityott:lock:analytics:aggregation`, TTL: 30s) preventing duplicate execution.
- **RBAC Security**: Consumes existing `ANALYTICS_VIEW` permission via `@rbacAuthorization.hasPermission(authentication, 'ANALYTICS_VIEW')`.
- **Authentication**: Zero modifications to OTP, JWT access tokens, refresh tokens, authentication filters, or security configuration.

---

## 3. Hardened API Contracts

### 3.1 Overview API
`GET /api/v1/analytics/overview`
- **Purpose**: High-level platform KPIs across an aggregated time window.
- **Parameters**:
  - `startDate` / `from` (optional, ISO-8601 `YYYY-MM-DD`, defaults to `today - 6 days`)
  - `endDate` / `to` (optional, ISO-8601 `YYYY-MM-DD`, defaults to `today`)
  - `timeWindow` (optional: `TODAY`, `YESTERDAY`, `LAST_7_DAYS`, `LAST_30_DAYS`, `CUSTOM`)
  - `platform` (optional: `IOS`, `ANDROID`, `WEB`)
- **Response**:
```json
{
  "success": true,
  "message": "Analytics overview retrieved successfully",
  "data": {
    "startDate": "2026-08-12",
    "endDate": "2026-08-18",
    "totalViews": 230,
    "totalPlays": 210,
    "uniqueViewers": 180,
    "totalWatchTimeSeconds": 121000,
    "averageSessionDurationSeconds": 526,
    "completedPlays": 155,
    "completionRate": 0.67,
    "bufferEvents": 6,
    "playbackErrors": 3,
    "qualityChanges": 11
  },
  "timestamp": "2026-08-18T13:45:00.000Z"
}
```

### 3.2 Content Analytics API
`GET /api/v1/analytics/content/{contentId}`
- **Purpose**: Deep-dive analytics for a single title or episode.
- **Parameters**:
  - `contentId` (path variable, mandatory)
  - `startDate` / `from`, `endDate` / `to`, `timeWindow`
- **Response**:
```json
{
  "success": true,
  "message": "Content analytics retrieved successfully",
  "data": {
    "contentId": 101,
    "title": "Telugu Cultural Showcase 1",
    "thumbnailUrl": "https://media.communityott.com/thumbnails/doc1.jpg",
    "startDate": "2026-08-12",
    "endDate": "2026-08-18",
    "totalViews": 150,
    "totalPlays": 135,
    "uniqueViewers": 120,
    "totalWatchTimeSeconds": 81000,
    "averageSessionDurationSeconds": 540,
    "completedPlays": 105,
    "completionRate": 0.70,
    "bufferEvents": 3,
    "playbackErrors": 1,
    "qualityChanges": 7
  },
  "timestamp": "2026-08-18T13:45:00.000Z"
}
```

### 3.3 Daily Trends API
`GET /api/v1/analytics/trends`
- **Purpose**: Daily time-series metric points for charting engagement curves.
- **Parameters**:
  - `startDate` / `from`, `endDate` / `to`, `timeWindow`
- **Response**:
```json
{
  "success": true,
  "message": "Daily trends retrieved successfully",
  "data": {
    "startDate": "2026-08-12",
    "endDate": "2026-08-18",
    "points": [
      {
        "date": "2026-08-17",
        "views": 80,
        "plays": 75,
        "uniqueViewers": 60,
        "watchTimeSeconds": 40000,
        "completionCount": 50,
        "bufferEvents": 3,
        "errors": 2
      },
      {
        "date": "2026-08-18",
        "views": 150,
        "plays": 135,
        "uniqueViewers": 120,
        "watchTimeSeconds": 81000,
        "completionCount": 105,
        "bufferEvents": 3,
        "errors": 1
      }
    ]
  },
  "timestamp": "2026-08-18T13:45:00.000Z"
}
```

### 3.4 Platform Analytics API
`GET /api/v1/analytics/platforms`
- **Purpose**: Platform distribution across iOS, Android, and Web clients.
- **Response**:
```json
{
  "success": true,
  "message": "Platform analytics retrieved successfully",
  "data": {
    "startDate": "2026-08-12",
    "endDate": "2026-08-18",
    "platforms": [
      {
        "platform": "IOS",
        "sessions": 100,
        "totalPlays": 90,
        "uniqueViewers": 80,
        "totalWatchTimeSeconds": 54000,
        "completionCount": 70,
        "errors": 1,
        "bufferEvents": 2
      },
      {
        "platform": "ANDROID",
        "sessions": 50,
        "totalPlays": 45,
        "uniqueViewers": 40,
        "totalWatchTimeSeconds": 27000,
        "completionCount": 35,
        "errors": 0,
        "bufferEvents": 1
      },
      {
        "platform": "WEB",
        "sessions": 80,
        "totalPlays": 75,
        "uniqueViewers": 60,
        "totalWatchTimeSeconds": 40000,
        "completionCount": 50,
        "errors": 2,
        "bufferEvents": 3
      }
    ]
  },
  "timestamp": "2026-08-18T13:45:00.000Z"
}
```

### 3.5 Top Content API (Rankings)
`GET /api/v1/analytics/content/top`
- **Parameters**:
  - `startDate` / `from`, `endDate` / `to`, `timeWindow`
  - `platform` (`IOS`, `ANDROID`, `WEB`)
  - `categoryId` (Long)
  - `languageId` (Long)
  - `sortBy` (`WATCH_TIME` [default], `VIEWS`, `UNIQUE_VIEWERS`, `COMPLETIONS`)
  - `sortDirection` (`DESC` [default], `ASC`)
  - `page` (Integer, default 0, min 0)
  - `size` (Integer, default 20, min 1, max 100)
- **Response**: Standard Spring Data `Page<ContentRankingItemDto>` metadata.

---

## 4. Date Range Contract & Time Windows
- **ISO-8601 Format**: `YYYY-MM-DD`.
- **Validation Rules**:
  - `startDate <= endDate`
  - Maximum window: **90 days**
  - Out-of-bounds violations throw `InvalidDateRangeException` -> HTTP 400 (`INVALID_DATE_RANGE`).
- **Supported Time Windows**:
  - `TODAY`: UTC start of day to UTC end of day.
  - `YESTERDAY`: Single previous UTC day.
  - `LAST_7_DAYS`: Rolling 7 days (`today - 6 days` to `today`).
  - `LAST_30_DAYS`: Rolling 30 days (`today - 29 days` to `today`).
  - `CUSTOM`: Requires explicit `startDate`/`endDate` or `from`/`to`.

---

## 5. Filter & Parameter Whitelists
- **Platform Whitelist**: `IOS`, `ANDROID`, `WEB`.
  - Violations throw `AnalyticsInvalidPlatformException` -> HTTP 400 (`ANALYTICS_INVALID_PLATFORM`).
- **Sort Metric Whitelist**: `WATCH_TIME`, `VIEWS`, `UNIQUE_VIEWERS`, `COMPLETIONS`.
  - Violations throw `AnalyticsInvalidSortException` -> HTTP 400 (`ANALYTICS_INVALID_SORT`).
- **Sort Direction Whitelist**: `ASC`, `DESC`.
  - Violations throw `AnalyticsInvalidSortException` -> HTTP 400 (`ANALYTICS_INVALID_SORT`).
- **Pagination Validation**:
  - `page >= 0`
  - `size >= 1 && size <= 100`
  - Violations throw `AnalyticsInvalidPaginationException` -> HTTP 400 (`ANALYTICS_INVALID_PAGINATION`).

---

## 6. Cache Strategy & Key Isolation
- **TTL**: 60 seconds.
- **Cache Isolation**: Keys contain all parameters to ensure zero cross-query cache bleeding:
  - `communityott:analytics:overview:{startDate}:{endDate}:{platform}`
  - `communityott:analytics:content:{contentId}:{startDate}:{endDate}`
  - `communityott:analytics:trends:{startDate}:{endDate}`
  - `communityott:analytics:platforms:{startDate}:{endDate}`
  - `communityott:analytics:top:{startDate}:{endDate}:{platform}:{categoryId}:{languageId}:{sortBy}:{sortDirection}:{page}:{size}`
- **Redis Resilience**: If Redis is unreachable, all queries transparently query PostgreSQL with zero user-facing 500 errors.

---

## 7. RBAC & Privacy Protections
- **`ANALYTICS_VIEW` Permission**: Enforced on every endpoint.
  - `MANAGER`: Allowed.
  - `SUPER_ADMIN`: Allowed.
  - `USER`: Rejected with HTTP 403 (`FORBIDDEN`).
  - Unauthenticated: Rejected with HTTP 401 (`UNAUTHORIZED`).
- **PII Stripping**: All analytics responses are strictly pre-aggregated. No individual user emails, phone numbers, IP addresses, tokens, or raw device identifiers are ever returned.

---

## 8. OpenAPI & Swagger Annotations
All controller methods are annotated with Swagger annotations (`@Tag`, `@Operation`, `@Parameter`, `@ApiResponses`, `@SecurityRequirement`) ensuring interactive API exploration on `/swagger-ui.html`.

---

## 9. Future Python / ML Contract
For the future Python analytics engine, the machine-to-machine data contract follows a flat, strongly typed `snake_case` schema:
```json
{
  "date": "2026-08-18",
  "content_id": 101,
  "platform": "IOS",
  "total_sessions": 100,
  "total_plays": 90,
  "unique_viewers": 80,
  "total_watch_time_seconds": 54000,
  "completion_count": 70,
  "pause_count": 15,
  "seek_count": 10,
  "buffer_event_count": 2,
  "error_count": 1,
  "quality_change_count": 5
}
```

---

## 10. Implemented vs Deferred

### Implemented:
- Strict parameter standardization and aliases (`from`/`to`, `startDate`/`endDate`).
- Date range bounds (max 90 days, `start <= end`) with HTTP 400 responses.
- Whitelisted platform filtering (`IOS`, `ANDROID`, `WEB`).
- Whitelisted sort metrics (`WATCH_TIME`, `VIEWS`, `UNIQUE_VIEWERS`, `COMPLETIONS`) and directions (`ASC`, `DESC`).
- Bounded database-level pagination (max 100).
- Category and language ranking filters.
- Complete platform distribution array (always includes `IOS`, `ANDROID`, `WEB`).
- Cache key parameter isolation (60s TTL).
- OpenAPI documentation on all endpoints.
- Integration tests (`AnalyticsApiHardeningTest` with 27 test scenarios).

### Deferred (Post-Phase 6.7):
- Python/FastAPI ML engine.
- Kafka / RabbitMQ message brokers.
- ClickHouse / Snowflake OLAP warehouses.
- Manager & Admin UI dashboards.
- DRM (FairPlay / Widevine).
