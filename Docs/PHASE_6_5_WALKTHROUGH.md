# CommunityOTT Phase 6.5: Playback Event & Telemetry Pipeline Walkthrough

## 1. Executive Summary

Phase 6.5 implements the **Playback Event & Telemetry Pipeline** for the CommunityOTT monolithic Spring Boot OTT backend.
This pipeline captures structured, real-time client playback events from iOS, Android, and future Web players (`PLAY`, `PAUSE`, `RESUME`, `SEEK`, `BUFFER_START`, `BUFFER_END`, `QUALITY_CHANGE`, `ERROR`, `HEARTBEAT`, `COMPLETE`, `END`).

It establishes a durable analytical foundation in PostgreSQL with client idempotency (UUID deduplication), bounded batch ingestion (up to 100 events), strict user session isolation, metadata payload capping (4KB), and transparent coordination with Watch Progress and Watch History services.

---

## 2. Playback Telemetry Architecture

```
+----------------------------------------------------------------------------------------------------+
|                                    CLIENT (iOS / Android / Web)                                    |
+----------------------------------------------------------------------------------------------------+
       |                                                           |
1. POST /content/{cId}/playback/sessions/{sId}/events       2. POST /content/{cId}/playback/sessions/{sId}/events/batch
   (Single Event Ingestion)                                    (Batch Event Ingestion - Max 100)
       |                                                           |
       +-----------------------------+-----------------------------+
                                     |
                                     v
+----------------------------------------------------------------------------------------------------+
|                                  PlaybackEventController                                           |
|                     (Derives userId strictly from CommunityOttPrincipal)                           |
+----------------------------------------------------------------------------------------------------+
                                     |
                                     v
+----------------------------------------------------------------------------------------------------+
|                                    PlaybackEventService                                            |
|  - Validates session ownership, liveness, and content binding                                      |
|  - Deduplicates via eventId database uniqueness & idempotent processing                            |
|  - Validates metadata size (max 4KB) and sequence numbers                                          |
|  - Dispatches progress/history updates for position events (PLAY, PAUSE, SEEK, HEARTBEAT, END)    |
+----------------------------------------------------------------------------------------------------+
          |                                                   |
          v                                                   v
+-----------------------+                           +------------------------------------+
|   WatchProgress /     |                           |       PlaybackEventRepository      |
|     WatchHistory      |                           |    (Persists to PostgreSQL V17)    |
+-----------------------+                           +------------------------------------+
```

---

## 3. Four Core OTT Discovery & Telemetry Concepts Compared

| Dimension | Playback Event (Phase 6.5) | Playback Session (Phase 6.1) | Watch Progress (Phase 6.1) | Watch History (Phase 6.2) |
| :--- | :--- | :--- | :--- | :--- |
| **Purpose** | "What happened during playback?" (Telemetry) | "Is this viewing session active?" | "Where should the user resume?" | "What did the user watch?" |
| **Table** | `playback_events` | `playback_sessions` | `watch_progress` | `watch_history` |
| **Volume** | High frequency (state changes, QoE) | 1 row per viewing attempt | 1 row per (user, content) | Append-only viewing log |
| **Idempotency** | `UNIQUE(event_id)` | Random opaque UUID token | Upsert on `(user_id, content_id)` | Appends on meaningful milestone |
| **HLS Tracking** | **Never tracks raw `.ts` segments** | Generates presigned URLs | Cursor only | Completed flag / audit |

---

## 4. Supported Playback Event Types (`PlaybackEventType`)

| Event Type | Description | Side Effect on Session / Progress |
| :--- | :--- | :--- |
| `PLAY` | Client initiated stream playback | Sets session `ACTIVE`, records progress/history |
| `PAUSE` | User paused playback | Sets session `PAUSED`, saves progress timestamp |
| `RESUME` | Playback resumed after pause/stall | Sets session `ACTIVE`, updates heartbeat |
| `SEEK` | Scrubbing to a new position | Updates session `lastPositionSeconds` & progress |
| `BUFFER_START` | Player ran out of buffered frames | Telemetry recorded (for QoE analytics) |
| `BUFFER_END` | Player finished buffering | Telemetry recorded, updates session `ACTIVE` |
| `QUALITY_CHANGE`| ABR rendition switch (e.g. 720p -> 1080p) | Telemetry recorded with rendition metadata |
| `ERROR` | Unrecoverable player error | Telemetry recorded with error code & message |
| `HEARTBEAT` | Periodic client keep-alive | Updates session heartbeat & progress |
| `COMPLETE` | Media reached >= 95% threshold | Marks `watch_progress.completed = true` |
| `END` | Player dismissed / closed | Gracefully ends `playback_sessions` |

---

## 5. Database Schema & Flyway Migration (`V17`)

Flyway migration `V17__create_playback_events_schema.sql`:
```sql
CREATE TABLE IF NOT EXISTS playback_events (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    playback_session_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content_id BIGINT NOT NULL,
    video_asset_id BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    position_seconds INTEGER NOT NULL DEFAULT 0,
    duration_seconds INTEGER NOT NULL DEFAULT 0,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    platform VARCHAR(32) NOT NULL DEFAULT 'WEB',
    device_id VARCHAR(255),
    session_sequence INTEGER,
    metadata VARCHAR(4000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_playback_events_event_id UNIQUE (event_id),
    CONSTRAINT fk_playback_events_session FOREIGN KEY (playback_session_id) REFERENCES playback_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_playback_events_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_playback_events_content FOREIGN KEY (content_id) REFERENCES content(id) ON DELETE CASCADE,
    CONSTRAINT fk_playback_events_video_asset FOREIGN KEY (video_asset_id) REFERENCES video_assets(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_playback_events_user_occurred ON playback_events(user_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_playback_events_content_occurred ON playback_events(content_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_playback_events_session_occurred ON playback_events(playback_session_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_playback_events_type_occurred ON playback_events(event_type, occurred_at);
```

---

## 6. API Endpoints & Request Specifications

### 1. Ingest Single Playback Event
- **Method**: `POST /api/v1/content/{contentId}/playback/sessions/{sessionId}/events`
- **Request Body**:
```json
{
  "eventId": "a9e6d034-71bb-457f-8566-a67b93198089",
  "type": "QUALITY_CHANGE",
  "positionSeconds": 120,
  "durationSeconds": 3600,
  "sequence": 4,
  "occurredAt": "2026-08-18T01:45:00Z",
  "metadata": {
    "from": "720p",
    "to": "1080p",
    "bitrateKbps": 4500
  }
}
```
- **Response**: `200 OK`
```json
{
  "success": true,
  "data": {
    "accepted": true,
    "eventId": "a9e6d034-71bb-457f-8566-a67b93198089",
    "receivedAt": "2026-08-18T01:45:00.120Z",
    "message": "Event accepted"
  },
  "message": "Playback event processed"
}
```

### 2. Ingest Batch Playback Events
- **Method**: `POST /api/v1/content/{contentId}/playback/sessions/{sessionId}/events/batch`
- **Constraints**: Maximum 100 events per batch.
- **Request Body**:
```json
{
  "events": [
    {
      "eventId": "e1",
      "type": "BUFFER_START",
      "positionSeconds": 30,
      "sequence": 1
    },
    {
      "eventId": "e2",
      "type": "BUFFER_END",
      "positionSeconds": 30,
      "sequence": 2
    }
  ]
}
```
- **Response**: `200 OK`
```json
{
  "success": true,
  "data": {
    "totalSubmitted": 2,
    "acceptedCount": 2,
    "duplicateCount": 0,
    "rejectedCount": 0,
    "results": [
      { "accepted": true, "eventId": "e1", "receivedAt": "...", "message": "Event accepted" },
      { "accepted": true, "eventId": "e2", "receivedAt": "...", "message": "Event accepted" }
    ]
  },
  "message": "Playback event batch processed"
}
```

---

## 7. Security, Isolation & Validation Rules

1. **Authentication & Principal Resolution**: Requires valid JWT Bearer token. `userId` is strictly derived from Spring Security (`CommunityOttPrincipal`).
2. **Session Verification**: The session token must exist, belong to the authenticated user, match the URL `contentId`, and not be `EXPIRED` or `ENDED` (except for `END` events).
3. **Idempotent Deduplication**: Re-submitting an existing `eventId` returns `200 OK` with `"Event already processed (idempotent)"` without raising duplicate key exceptions or creating orphan records.
4. **Position Bounds**: Positions < 0 or > duration + 10s return `400 Bad Request`.
5. **Metadata Sanitization**: Metadata payloads are restricted to a maximum length of 4000 characters (<= 4KB) to prevent database denial-of-service.

---

## 8. Mobile Integration Contracts (iOS & Android)

- **iOS AVPlayer / AVPlayerItem**:
  - Emits `PLAY` on `AVPlayer.play()`.
  - Emits `PAUSE` on `AVPlayer.pause()`.
  - Emits `BUFFER_START` / `BUFFER_END` on `AVPlayerItem.isPlaybackLikelyToKeepUp` transitions.
  - Emits `QUALITY_CHANGE` from `AVPlayerItemAccessLogEvent.indicatedBitrate`.
  - Emits `ERROR` from `AVPlayerItemFailedToPlayToEndTime`.
- **Android ExoPlayer**:
  - Emits `PLAY` / `PAUSE` via `Player.Listener.onPlayWhenReadyChanged`.
  - Emits `BUFFER_START` / `BUFFER_END` via `Player.STATE_BUFFERING` / `Player.STATE_READY`.
  - Emits `QUALITY_CHANGE` via `AnalyticsListener.onVideoSizeChanged` or `onDownstreamFormatChanged`.
  - Emits `ERROR` via `Player.Listener.onPlayerError`.

---

## 9. Future Python Analytics Pipeline

```
[ iOS / Android / Web ]
         | (HTTPS Telemetry Events)
         v
[ Spring Boot CommunityOTT Backend ]
         | (PostgreSQL durable storage: playback_events)
         v
[ Future Analytics Worker / CDC ]
         | (Periodic batch export / Redis stream)
         v
[ Python Analytics Engine (Pandas / FastAPI) ]
         |
         +--> Hourly / Daily Aggregations (Watch Time, Completion Rate, Buffer Ratio)
         +--> QoE Error Matrix (By ISP, Device, Rendition)
         v
[ Manager & Admin Insights Dashboards ]
```

---

## 10. Scope Status: Implemented vs. Deferred

### Implemented in Phase 6.5:
- [x] Flyway Migration `V17__create_playback_events_schema.sql`
- [x] `PlaybackEventType` enum (11 strongly typed event types)
- [x] `PlaybackEvent` JPA entity with UUID uniqueness and audit timestamps
- [x] `PlaybackEventRepository` with session and user chronological queries
- [x] `PlaybackEventService` with validation, idempotency, batching, metadata limits, and progress synchronization
- [x] `PlaybackEventController` exposing single and batch event endpoints
- [x] Decoupling from raw HLS segment tracking
- [x] 18 integration tests in `PlaybackEventTelemetryTest.java`

### Deferred to Future Phases:
- **Python Analytics Service / FastAPI Engine**: (Deferred to Analytics module).
- **Kafka / Event Streaming Broker**: (Deferred to High-Scale streaming phase).
- **ClickHouse / OLAP Data Warehouse**: (Deferred).
- **QoE Manager Dashboards & Real-time Metrics**: (Deferred to Dashboard module).
