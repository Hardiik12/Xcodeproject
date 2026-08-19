# Analytics Data Contract v1 (`analytics-contract-v1`)

## 1. Document Overview & Purpose
This document defines the formal, versioned machine-to-machine data contract between the **CommunityOTT Spring Boot Monolithic Backend** (producer) and future downstream analytics, data-engineering, and ML pipelines such as **Python / FastAPI analytics microservices** (consumers).

The contract exposes pre-aggregated, privacy-safe playback and engagement metrics. Downstream consumers consume only this public schema and do **not** access transactional databases, auth tables, OTP state, Redis caches, or raw telemetry event logs.

---

## 2. Contract Metadata
- **Contract Version**: `analytics-contract-v1`
- **Producer**: CommunityOTT Spring Boot Backend (`/api/v1/analytics/export`)
- **Consumer**: Future Python/FastAPI Analytics & Machine Learning Services
- **Data Source**: Aggregated daily metric tables (`analytics_daily_metrics`)
- **Aggregation Timezone**: `UTC` (One record per calendar day in UTC)
- **Serialization Format**: `application/json` (UTF-8)
- **Naming Convention**: `snake_case` (All field names in the export schema use lowercase alphanumeric characters with underscores)

---

## 3. Data Schema & Field Definitions

### 3.1. Envelope Specification
```json
{
  "contract_version": "analytics-contract-v1",
  "generated_at": "2026-08-19T01:30:00Z",
  "from": "2026-08-01",
  "to": "2026-08-18",
  "page": 0,
  "size": 100,
  "total_records": 420,
  "total_pages": 5,
  "has_next": true,
  "records": [ ... ]
}
```

| Envelope Field | Type | Required | Description |
| :--- | :--- | :---: | :--- |
| `contract_version` | `string` | Yes | Contract version identifier, strictly `"analytics-contract-v1"`. |
| `generated_at` | `string` (ISO-8601) | Yes | UTC timestamp when the export payload was generated. |
| `from` | `string` (YYYY-MM-DD) | Yes | Effective start date of the exported window. |
| `to` | `string` (YYYY-MM-DD) | Yes | Effective end date of the exported window. |
| `page` | `integer` | Yes | Zero-indexed page number of the returned dataset. |
| `size` | `integer` | Yes | Maximum page size requested/returned (1–100). |
| `total_records` | `integer / long` | Yes | Total matching records across all pages. |
| `total_pages` | `integer` | Yes | Total available pages. |
| `has_next` | `boolean` | Yes | True if more pages exist (`page + 1 < total_pages`). |
| `records` | `array` | Yes | List of daily aggregated metric records. |

### 3.2. Record Field Definitions
```json
{
  "date": "2026-08-18",
  "content_id": 42,
  "category_id": 3,
  "language_id": 1,
  "platform": "IOS",
  "sessions": 120,
  "plays": 150,
  "unique_viewers": 95,
  "watch_time_seconds": 42000,
  "completed_plays": 70,
  "completion_rate": 0.4667,
  "buffering_events": 12,
  "playback_errors": 2,
  "quality_changes": 8
}
```

| Record Field | Data Type | Nullable | Description |
| :--- | :--- | :---: | :--- |
| `date` | `string` (YYYY-MM-DD) | No | Calendar day of metric aggregation in UTC. |
| `content_id` | `integer / long` | No | Identifier of the Content item. |
| `category_id` | `integer / long` | Yes | Primary category identifier if mapped, else `null`. |
| `language_id` | `integer / long` | Yes | Original language identifier if mapped, else `null`. |
| `platform` | `string` (`IOS` \| `ANDROID` \| `WEB`) | No | Client playback platform. |
| `sessions` | `integer / long` | No | Total playback sessions initialized on that day. |
| `plays` | `integer / long` | No | Total active stream starts (play intentions). |
| `unique_viewers` | `integer / long` | No | Distinct authenticated user count on that day. |
| `watch_time_seconds` | `integer / long` | No | Total accumulated watch duration in seconds. |
| `completed_plays` | `integer / long` | No | Total playback sessions that reached completion. |
| `completion_rate` | `decimal` (4 decimals) | No | `plays > 0 ? (completed_plays / plays) : 0.0` (never NaN/null). |
| `buffering_events` | `integer / long` | No | Total player buffer stall events recorded. |
| `playback_errors` | `integer / long` | No | Total playback error events recorded. |
| `quality_changes` | `integer / long` | No | Total adaptive bitrate (ABR) rendition shifts. |

---

## 4. Operational Boundaries & Rules

### 4.1. Privacy & Zero-PII Policy
The export schema strictly forbids exposure of user-identifiable data:
- **No** user IDs, usernames, email addresses, or phone numbers.
- **No** IP addresses, geographical coordinates, or user-agent strings.
- **No** session tokens, JWTs, OTP secrets, or device hardware IDs.
- **No** raw playback event identifiers or timestamps.

### 4.2. Completion Rate Calculation
$$\text{completion\_rate} = \begin{cases} \text{round}\left(\frac{\text{completed\_plays}}{\text{plays}}, 4\right) & \text{if } \text{plays} > 0 \\ 0.0 & \text{if } \text{plays} = 0 \end{cases}$$

### 4.3. Pagination & Query Limits
- **Default Page Size**: `100` records.
- **Maximum Page Size**: `100` records.
- **Maximum Date Window**: `90` calendar days (`ChronoUnit.DAYS.between(from, to) <= 90`).
- **Database Projection**: Executed via direct database projections to prevent memory exhaustion and N+1 query overhead.

---

## 5. API Endpoint Reference

- **HTTP Method & Path**: `GET /api/v1/analytics/export`
- **Required Authorization**: `ANALYTICS_VIEW` (held by `MANAGER` and `SUPER_ADMIN`). Standard users receive `403 Forbidden`. Unauthenticated requests receive `401 Unauthorized`.
- **Query Parameters**:
  - `from` (`LocalDate`, optional, default: `to - 6 days`): Window start date.
  - `to` (`LocalDate`, optional, default: `today UTC`): Window end date.
  - `platform` (`String`, optional): Filter by `IOS`, `ANDROID`, or `WEB`.
  - `content_id` (`Long`, optional): Filter by specific Content ID.
  - `category_id` (`Long`, optional): Filter by Category ID.
  - `language_id` (`Long`, optional): Filter by Language ID.
  - `page` (`int`, optional, default: `0`): Zero-based page index.
  - `size` (`int`, optional, default: `100`, max: `100`): Page size limit.

---

## 6. Contract Evolution & Versioning Policy

1. **Backward-Compatible Changes** (Minor updates within `analytics-contract-v1`):
   - Adding a new optional top-level metadata field to the envelope.
   - Adding a new nullable numeric or string field to the record.
2. **Breaking Changes** (Requires incrementing to `analytics-contract-v2`):
   - Renaming any existing field (e.g. `content_id` $\rightarrow$ `item_id`).
   - Changing the data type of an existing field (e.g. integer to string).
   - Changing calculation formulas or semantics of existing fields.
   - Removing any existing record or envelope field.
