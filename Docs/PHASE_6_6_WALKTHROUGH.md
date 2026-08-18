# Phase 6.6 Walkthrough: Analytics Aggregation Foundation

---

## 1. Analytics Architecture Overview

The CommunityOTT Analytics Aggregation subsystem bridges raw playback telemetry and high-performance administrative dashboards within our monolithic Spring Boot backend. It converts unbounded event streams into deterministic, query-optimized daily aggregate metrics stored in PostgreSQL and cached in Redis.

```
                    +-------------------------------------------------------------+
                    |                      Client Platforms                       |
                    |                   (iOS / Android / Web)                     |
                    +-------------------------------------------------------------+
                                                   |
                                                   | (POST /playback/sessions/{id}/events)
                                                   v
                    +-------------------------------------------------------------+
                    |                    Raw Telemetry Layer                      |
                    |                   (playback_events table)                   |
                    +-------------------------------------------------------------+
                                                   |
                                                   | Incremental Stream (Cursor batch)
                                                   v
                    +-------------------------------------------------------------+
                    |                AnalyticsAggregationService                  |
                    |   - High-water mark cursor tracking (Checkpoint table)      |
                    |   - Idempotent upsert logic (ON CONFLICT DO UPDATE)         |
                    |   - Distributed Redis execution lock (TTL: 30s)             |
                    +-------------------------------------------------------------+
                                                   |
                                                   | Aggregated Metrics
                                                   v
                    +-------------------------------------------------------------+
                    |                     Aggregated Storage                      |
                    |             (analytics_daily_metrics table)                 |
                    +-------------------------------------------------------------+
                                                   |
                                                   | Cached Dashboard Queries (TTL: 60s)
                                                   v
                    +-------------------------------------------------------------+
                    |                    AnalyticsQueryService                    |
                    |   - Overview, Content, Trend, Platform & Ranking APIs       |
                    |   - Redis caching for frequent manager/admin queries        |
                    +-------------------------------------------------------------+
                                                   |
                                                   | Secured via ANALYTICS_VIEW
                                                   v
                    +-------------------------------------------------------------+
                    |                Manager / Admin Dashboards                   |
                    |           (Future Export -> Python ML Pipeline)             |
                    +-------------------------------------------------------------+
```

---

## 2. Raw Events vs. Aggregates

| Dimension | Raw Telemetry (`playback_events`) | Pre-Aggregated Metrics (`analytics_daily_metrics`) |
| :--- | :--- | :--- |
| **Primary Question** | *"What exact event happened at what millisecond?"* | *"How did this content and platform perform on this day?"* |
| **Granularity** | Point-in-time event (UUID `eventId`, millisecond timestamp) | Daily summary tuple `(metric_date, content_id, platform)` |
| **Lifecycle** | Append-only, immutable durable ledger | Incrementally updated or rebuilt idempotently |
| **Query Target** | Debugging, forensic inspection, ML ingestion pipelines | Dashboard overviews, time-series trends, content popularity rankings |
| **Data Retention** | Permanent or archived to cold object storage | Permanent online OLTP/OLAP tables |

> [!IMPORTANT]
> Raw events are **NEVER destroyed or truncated** during aggregation. Raw playback events remain the immutable ground truth for future forensic audit and Python ML workflows.

---

## 3. Metric Definitions & Formulas

1. **Total Views / Sessions**:
   - **Formula**: $\sum \text{unique sessions per date, content, platform}$
   - **Source**: `playback_events.playback_session_id`
2. **Total Plays**:
   - **Formula**: $\text{COUNT}(id) \text{ where } event\_type = 'PLAY'$
   - **Source**: `PLAY` events emitted when playback initiates.
3. **Unique Viewers**:
   - **Formula**: $\text{COUNT(DISTINCT } user\_id)$
   - **Source**: `playback_events.user_id`
4. **Total Watch Time**:
   - **Formula**: $\sum \text{position\_seconds delta} + \sum \text{heartbeat intervals (30s)}$
   - **Source**: `position_seconds` progress & `HEARTBEAT` events.
5. **Average Session Duration**:
   - **Formula**: $\frac{\text{Total Watch Time (seconds)}}{\text{Total Views / Sessions}}$
   - **Source**: Computed ratio.
6. **Completed Plays**:
   - **Formula**: $\text{COUNT}(id) \text{ where } event\_type = 'COMPLETE'$
   - **Source**: `COMPLETE` events triggered at end of stream.
7. **Completion Rate**:
   - **Formula**: $\frac{\text{Completed Plays}}{\text{Total Sessions}}$ (Range: $0.0 \dots 1.0$)
   - **Source**: Computed ratio.
8. **Pause Count**:
   - **Formula**: $\text{COUNT}(id) \text{ where } event\_type = 'PAUSE'$
9. **Seek Count**:
   - **Formula**: $\text{COUNT}(id) \text{ where } event\_type = 'SEEK'$
10. **Buffering Events**:
    - **Formula**: $\text{COUNT}(id) \text{ where } event\_type = 'BUFFER\_START'$
11. **Playback Errors**:
    - **Formula**: $\text{COUNT}(id) \text{ where } event\_type = 'ERROR'$
12. **Quality Changes**:
    - **Formula**: $\text{COUNT}(id) \text{ where } event\_type = 'QUALITY\_CHANGE'$

---

## 4. Aggregation Strategy & Checkpoint Cursor

- **Cursor Mechanics**:
  - The checkpoint table `analytics_aggregation_checkpoint` records the `last_processed_event_id` and `last_processed_occurred_at` for consumer `"DEFAULT_DAILY_AGGREGATOR"`.
  - The aggregation worker pulls batches of unprocessed events using:
    `findByIdGreaterThanOrderByIdAsc(lastId, PageRequest.of(0, batchSize))`
- **Idempotency & Recomputation**:
  - Aggregation groups events in memory by `(metric_date, content_id, platform)` and merges into existing `analytics_daily_metrics` records.
  - The unique constraint `uq_analytics_daily_metric` on `(metric_date, content_id, platform)` ensures that duplicate rows cannot be inserted.

---

## 5. Time Windows & Range Validation

- Supported standard windows:
  - **Today**: Current date (UTC)
  - **Yesterday**: `today - 1`
  - **Last 7 Days**: `today - 6` to `today` (Default)
  - **Last 30 Days**: `today - 29` to `today`
  - **Custom Range**: Explicit `startDate` and `endDate` query parameters.
- **Safety Bounds**:
  - `startDate` cannot be after `endDate` (Throws `InvalidDateRangeException` -> `400 Bad Request`).
  - Max range is restricted to **90 days** to prevent unbounded full-table scans.

---

## 6. PostgreSQL Schema Design (`V18__create_analytics_aggregation_schema.sql`)

```sql
CREATE TABLE IF NOT EXISTS analytics_daily_metrics (
    id BIGSERIAL PRIMARY KEY,
    metric_date DATE NOT NULL,
    content_id BIGINT NOT NULL,
    platform VARCHAR(32) NOT NULL DEFAULT 'WEB',
    total_sessions INTEGER NOT NULL DEFAULT 0,
    total_plays INTEGER NOT NULL DEFAULT 0,
    unique_viewers INTEGER NOT NULL DEFAULT 0,
    total_watch_time_seconds BIGINT NOT NULL DEFAULT 0,
    completion_count INTEGER NOT NULL DEFAULT 0,
    pause_count INTEGER NOT NULL DEFAULT 0,
    seek_count INTEGER NOT NULL DEFAULT 0,
    buffer_event_count INTEGER NOT NULL DEFAULT 0,
    error_count INTEGER NOT NULL DEFAULT 0,
    quality_change_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_analytics_daily_metric UNIQUE (metric_date, content_id, platform),
    CONSTRAINT fk_analytics_daily_content FOREIGN KEY (content_id) REFERENCES content(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_analytics_daily_metric_date ON analytics_daily_metrics(metric_date DESC);
CREATE INDEX IF NOT EXISTS idx_analytics_daily_content_date ON analytics_daily_metrics(content_id, metric_date DESC);
CREATE INDEX IF NOT EXISTS idx_analytics_daily_platform_date ON analytics_daily_metrics(platform, metric_date DESC);

CREATE TABLE IF NOT EXISTS analytics_aggregation_checkpoint (
    id BIGSERIAL PRIMARY KEY,
    consumer_name VARCHAR(64) NOT NULL,
    last_processed_event_id BIGINT NOT NULL DEFAULT 0,
    last_processed_occurred_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_analytics_checkpoint_consumer UNIQUE (consumer_name)
);
```

---

## 7. Redis Caching & Distributed Lock

1. **Distributed Aggregation Lock**:
   - **Key**: `communityott:lock:analytics:aggregation`
   - **TTL**: 30 seconds
   - Prevents concurrent scheduled triggers and manual triggers from double-processing the same batch.
2. **Dashboard Query Cache**:
   - **Keys**:
     - `communityott:analytics:overview:{startDate}:{endDate}`
     - `communityott:analytics:content:{contentId}:{startDate}:{endDate}`
     - `communityott:analytics:trends:{startDate}:{endDate}`
     - `communityott:analytics:platforms:{startDate}:{endDate}`
   - **TTL**: 60 seconds
   - **Invalidation**: Cleared automatically upon completion of any aggregation job.
3. **Resilience**:
   - If Redis connection fails or times out, the service logs a warning and directly falls back to PostgreSQL queries.

---

## 8. RBAC & Security

- **Permission**: `ANALYTICS_VIEW`
- **Authorized Roles**:
  - `MANAGER`: Granted `ANALYTICS_VIEW` (and `ANALYTICS_EXPORT`).
  - `SUPER_ADMIN`: Full platform-wide analytics access.
- **Unauthorized Roles**:
  - `USER`: Receives `403 Forbidden`.
  - Unauthenticated requests: Receive `401 Unauthorized`.
- **Privacy Enforcement**:
  - Aggregation endpoints never return user IDs, emails, IP addresses, or raw event payloads. Only aggregated business and QoE metrics are exposed.

---

## 9. Analytics API Reference

| HTTP Method | Path | Description | Required Permission |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/analytics/overview` | Platform-wide aggregate metrics | `ANALYTICS_VIEW` |
| `GET` | `/api/v1/analytics/content/{contentId}` | Content-specific aggregate performance | `ANALYTICS_VIEW` |
| `GET` | `/api/v1/analytics/trends` | Time-series daily metrics | `ANALYTICS_VIEW` |
| `GET` | `/api/v1/analytics/platforms` | Breakdown by client platform (iOS, Android, Web) | `ANALYTICS_VIEW` |
| `GET` | `/api/v1/analytics/content/top` | Ranked content by watch time, views, or unique viewers | `ANALYTICS_VIEW` |
| `POST` | `/api/v1/analytics/aggregate` | Trigger incremental aggregation job | `ANALYTICS_VIEW` |

---

## 10. Future Python Analytics Pipeline Integration

```
                    Raw Playback Events
                           │
                           ▼
                  Spring Boot Backend
                           │
                           ▼
                  Analytics Aggregates
                           │
                    ┌──────┴──────┐
                    │             │
                    ▼             ▼
              Dashboards       Export
                                  │
                                  ▼
                               Python
                                  │
                    ┌─────────────┼─────────────┐
                    ▼             ▼             ▼
                Retention    Forecasting   Recommendations
```

- **Clean Decoupling**: Future Python data science workflows will consume exported datasets (`AnalyticsExportService`) or read-only analytical views rather than directly querying high-churn transactional production tables (`users`, `auth_sessions`, `playback_sessions`).
- **Deferred Advanced Workflows**:
  - ML Retention & Churn Prediction
  - Cohort Analysis
  - Collaborative Filtering / Recommendations
  - Anomaly Detection for CDN Delivery
  - Complex Bitrate / QoE buffer modeling

---

## 11. Test Coverage Summary

- **`AnalyticsAggregationTest`**: 14 integration tests covering:
  1. Aggregation pipeline event processing and daily metrics generation.
  2. Incremental aggregation and cursor checkpoint progression.
  3. Overview endpoint computation and schema verification.
  4. Content analytics retrieval.
  5. Daily trends time-series serialization.
  6. Platform distribution breakdowns.
  7. Content ranking and pagination.
  8. Manual aggregation trigger.
  9. RBAC permission rejection for standard users (403).
  10. Unauthenticated access rejection (401).
  11. Invalid date range rejection (400).
  12. Range exceeding 90 days rejection (400).
  13. Content not found (404).
  14. Zeroed response on empty dataset.
- **Full Backend Test Suite**: **358/358 passing tests, 0 failures, 0 errors, 0 skipped**.
