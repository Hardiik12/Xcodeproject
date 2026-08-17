# Phase 6.1 — Playback Session + Watch Progress Foundation Walkthrough

## 1. Overview & Architecture
In **Phase 6.1**, we established the core playback session lifecycle and persistent watch progress foundation for the CommunityOTT monolithic Spring Boot backend. This foundation decouples authentication sessions (login/device credentials) from playback sessions (ephemeral viewing sessions for specific media assets) and persists watch progress durably in PostgreSQL as the single source of truth for resume positions and continue watching.

```
+---------------------------------------------------------------------------------------------+
|                                    CLIENT (iOS / Android / Web)                             |
+---------------------------------------------------------------------------------------------+
               |                                               |                        |
  1. POST /playback/sessions                       2. POST /heartbeat        3. POST /end
     (Start session & resume pos)                    (Periodic / progress)      (Finalize)
               v                                               v                        v
+---------------------------------------------------------------------------------------------+
|                                SPRING BOOT MONOLITHIC BACKEND                               |
|                                                                                             |
|   +------------------------------------+   +--------------------------------------------+   |
|   | RbacAuthorization (VIDEO_VIEW)     |   | PlaybackSessionRateLimiter (Redis Window)  |   |
|   +------------------------------------+   +--------------------------------------------+   |
|                                         |                                                   |
|                                         v                                                   |
|                      +---------------------------------------+                              |
|                      |        PlaybackSessionService         |                              |
|                      +---------------------------------------+                              |
|                          /              |                 \                                 |
|                         /               |                  \                                |
|                        v                v                   v                               |
|           +-----------------------+ +---------------------+ +----------------------------+  |
|           | MediaDeliveryService  | | ContentAccessService| |    WatchProgressService    |  |
|           | (HLS / CDN / MinIO)   | | (Published / Ready) | | (PostgreSQL Source of Truth|  |
|           +-----------------------+ +---------------------+ +----------------------------+  |
+-----------------------------------------|---------------------------------|-----------------+
                                          |                                 |
                                          v                                 v
                     +---------------------------+       +-----------------------------+
                     |    playback_sessions      |       |       watch_progress        |
                     | (PostgreSQL State Machine)|       |  (Durable Resume/Progress)  |
                     +---------------------------+       +-----------------------------+
```

---

## 2. Authentication Session vs. Playback Session

| Dimension | Authentication Session (`AuthSession`) | Playback Session (`PlaybackSession`) |
| :--- | :--- | :--- |
| **Domain** | Security, Identity, Multi-device Auth | Media consumption, player playback state |
| **Duration** | Long-lived (days to weeks with refresh token rotation) | Short-lived (duration of a viewing session) |
| **Identifier** | Database internal ID + Refresh Token Hash | Cryptographically random public UUID |
| **State Machine** | ACTIVE / REVOKED / EXPIRED | STARTED -> ACTIVE -> PAUSED -> ENDED / EXPIRED |
| **Storage** | PostgreSQL `auth_sessions` | PostgreSQL `playback_sessions` |

---

## 3. Database Models & Schema (Flyway V13)

### `playback_sessions` Table
Tracks the temporal lifecycle of every video viewing instance across iOS, Android, and Web clients.
- `id`: BIGSERIAL PRIMARY KEY
- `session_id`: VARCHAR(64) NOT NULL UNIQUE (public opaque identifier)
- `user_id`: BIGINT NOT NULL (FK to `users.id` ON DELETE CASCADE)
- `content_id`: BIGINT NOT NULL (FK to `content.id` ON DELETE CASCADE)
- `video_asset_id`: BIGINT NOT NULL (FK to `video_assets.id` ON DELETE CASCADE)
- `device_id`: VARCHAR(255) (App-generated client device UUID)
- `platform`: VARCHAR(32) NOT NULL DEFAULT 'WEB' (`IOS`, `ANDROID`, `WEB`)
- `status`: VARCHAR(32) NOT NULL DEFAULT 'STARTED' (`STARTED`, `ACTIVE`, `PAUSED`, `ENDED`, `EXPIRED`)
- `last_position_seconds`: INTEGER NOT NULL DEFAULT 0
- `duration_seconds`: INTEGER NOT NULL DEFAULT 0
- `started_at`: TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
- `last_heartbeat_at`: TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
- `ended_at`: TIMESTAMP WITH TIME ZONE
- `created_at`, `updated_at`, `version`: Optimistic locking and audit timestamps

### `watch_progress` Table
Durable source of truth for resume positions and completed status per user and content item.
- `id`: BIGSERIAL PRIMARY KEY
- `user_id`: BIGINT NOT NULL (FK to `users.id` ON DELETE CASCADE)
- `content_id`: BIGINT NOT NULL (FK to `content.id` ON DELETE CASCADE)
- `video_asset_id`: BIGINT NOT NULL (FK to `video_assets.id` ON DELETE CASCADE)
- `position_seconds`: INTEGER NOT NULL DEFAULT 0
- `duration_seconds`: INTEGER NOT NULL DEFAULT 0
- `completion_percentage`: DOUBLE PRECISION NOT NULL DEFAULT 0.0
- `completed`: BOOLEAN NOT NULL DEFAULT FALSE
- `last_watched_at`: TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
- `created_at`, `updated_at`, `version`: Optimistic locking and audit timestamps
- **Unique Constraint**: `uq_watch_progress_user_content (user_id, content_id)` ensures one progress record per user per title.

---

## 4. Playback Session State Machine & Inactivity Expiration

```
               +-----------+
               |  STARTED  |
               +-----------+
                 /   |   \
                /    |    \
       Heartbeat/   Pause  \ Timeout / Expiry
       Progress      |      \
              v      v       v
         +--------+ <-> +--------+      +---------+
         | ACTIVE |     | PAUSED | ---> | EXPIRED |
         +--------+ <-> +--------+      +---------+
              \          /                   ^
               \        /                    |
             End \    / End                  | Timeout
                  v  v                       |
              +-------+                      |
              | ENDED | ---------------------+
              +-------+
```

- **Transitions**:
  - `STARTED` -> `ACTIVE`, `PAUSED`, `ENDED`, `EXPIRED`
  - `ACTIVE` -> `PAUSED`, `ENDED`, `EXPIRED`
  - `PAUSED` -> `ACTIVE`, `ENDED`, `EXPIRED`
  - `ENDED` & `EXPIRED` are terminal states.
- **Inactivity Timeout**: Configured via `communityott.playback.session-inactivity-timeout-minutes` (default: 5 min). Stale sessions without heartbeat are automatically marked `EXPIRED` by background reconciliation.

---

## 5. Watch Progress & Completion Policy

1. **Resume Position Calculation**:
   - When a session starts, `WatchProgressService` retrieves the existing `position_seconds` for `(userId, contentId)`. If no record exists, returns `0`.
2. **Completion Threshold**:
   - Configurable via `communityott.playback.completion-threshold-percent` (default: 95.0%).
   - When `completionPercentage >= 95.0%`, `completed` is permanently marked `true`.
3. **Rewatch & Replay Policy**:
   - If a user replays a completed title from the beginning (e.g. position 0s), the existing row is updated with the new playback position, while `completed` remains `true` (retaining historical completion state).

---

## 6. Endpoints & API Contract

### 1. `POST /api/v1/content/{contentId}/playback/sessions`
Starts playback session and returns authorization, playback URL, and resume position.
- **Request**:
```json
{
  "deviceId": "iphone-15-pro-uuid",
  "platform": "IOS"
}
```
- **Response (201 Created)**:
```json
{
  "success": true,
  "message": "Playback session started successfully",
  "data": {
    "playbackSessionId": "e527dc8ab37746c8b3bb9e4536b46fd3",
    "contentId": 101,
    "title": "Telugu Handloom Weaving Traditions",
    "protocol": "HLS",
    "playbackUrl": "http://localhost:9000/communityott-media/hls/101/202/master.m3u8?...",
    "startedAt": "2026-08-17T11:00:00Z",
    "expiresAt": "2026-08-17T11:15:00Z",
    "durationSeconds": 3600,
    "resumePositionSeconds": 1320,
    "deliveryMode": "LOCAL",
    "deliveryProvider": "MINIO_LOCAL",
    "availableRenditions": [
      {
        "resolution": "1080p",
        "width": 1920,
        "height": 1080,
        "bandwidthBps": 5000000,
        "averageBandwidthBps": 4500000,
        "codecs": "avc1.640028,mp4a.40.2",
        "frameRate": 24.0
      }
    ]
  }
}
```

### 2. `POST /api/v1/content/{contentId}/playback/sessions/{sessionId}/heartbeat`
Lightweight periodic heartbeat to maintain session liveness (recommended every 10–15s).
- **Request**:
```json
{
  "positionSeconds": 1350
}
```
- **Response (200 OK)**: Returns updated `PlaybackSessionStatusDto`.

### 3. `POST /api/v1/content/{contentId}/playback/sessions/{sessionId}/progress`
Updates current playback position and computes completion percentage.
- **Request**:
```json
{
  "positionSeconds": 1800,
  "durationSeconds": 3600
}
```
- **Response (200 OK)**: Returns `WatchProgressDto`.

### 4. `POST /api/v1/content/{contentId}/playback/sessions/{sessionId}/end`
Closes the session gracefully. Idempotent.
- **Response (200 OK)**: Returns `PlaybackSessionStatusDto` with `status: "ENDED"`.

### 5. `GET /api/v1/content/{contentId}/playback/sessions/{sessionId}`
Retrieves current session state for the authenticated owner.

---

## 7. Security & Protection Controls

- **User Ownership Verification**: `userId` is strictly resolved from the authenticated JWT token / SecurityContext (`@AuthenticationPrincipal CommunityOttPrincipal`). Client cannot spoof or inject `userId`.
- **Session Boundary**: Cross-user session access or progress manipulation is blocked with HTTP 403 (`PLAYBACK_SESSION_ACCESS_DENIED`).
- **No Secret Leakage**: No MinIO secrets, database IDs, password hashes, or internal storage paths are exposed in API payloads.
- **Rate Limiting**: Session creation is limited to 15/min and progress/heartbeats to 60/min via Redis window keys.

---

## 8. Verification & Test Suite Summary

- **Total Backend Tests**: **283 passed** (0 failures, 0 errors, 0 skipped).
- **Test Suite**: `PlaybackSessionAndProgressTest.java` (27 comprehensive integration tests covering all lifecycle, resume, completion, security, and concurrency scenarios).
- **Flyway & Hibernate**: `V13__create_playback_session_and_watch_progress.sql` validated with `ddl-auto=validate`.

---

## 9. Deferred Work (Strict Scope Boundaries)
- Continue Watching aggregation API (Phase 6.2).
- Watch History browsing & clear API (Phase 6.3).
- Playback QoE Analytics and aggregation dashboard (Phase 7.x).
- DRM (FairPlay / Widevine).
