# Phase 6.2 — Watch History System Walkthrough

## 1. Overview & Architecture
In **Phase 6.2**, we implemented a durable, high-performance **Watch History** system for the CommunityOTT monolithic Spring Boot backend. 

Watch History answers:
> *"What has this user watched recently?"* (Chronological viewing activity, newest first)

This is explicitly decoupled from Watch Progress (Phase 6.1):
> *"Where should this user resume?"* (Exact timestamp bookmark cursor for continue watching)

```
+----------------------------------------------------------------------------------------------------+
|                                    CLIENT (iOS / Android / Web)                                    |
+----------------------------------------------------------------------------------------------------+
       |                                       |                                        |
  1. GET /api/v1/users/me/history    2. DELETE .../history/{contentId}       3. DELETE .../history
     (Paginated recent history)          (Remove single title)                  (Clear all history)
       v                                       v                                        v
+----------------------------------------------------------------------------------------------------+
|                                   WatchHistoryController                                           |
|                     (Derives userId strictly from CommunityOttPrincipal)                           |
+----------------------------------------------------------------------------------------------------+
                                               |
                                               v
+----------------------------------------------------------------------------------------------------+
|                                     WatchHistoryService                                            |
|  - getHistoryForUser(userId, pageable) -> Page<WatchHistoryResponse>                               |
|  - deleteHistoryItem(userId, contentId) (Idempotent single item deletion)                         |
|  - clearHistoryForUser(userId) (Transactional complete wipe for user)                              |
|  - recordViewing(userId, content, sessionId, pos, dur, deviceId, platform)                         |
+----------------------------------------------------------------------------------------------------+
                                               ^
                                               | (Triggered on meaningful playback progress/heartbeat)
+----------------------------------------------------------------------------------------------------+
|                             PlaybackSessionService (Phase 6.1)                                     |
|  - recordProgress(...)  --------------------+                                                      |
|  - recordHeartbeat(...) --------------------+                                                      |
|  - endSession(...)      --------------------+                                                      |
+----------------------------------------------------------------------------------------------------+
                                               |
                                               v
                                   +-----------------------+
                                   |     watch_history     |
                                   |  (PostgreSQL V14 DDL) |
                                   +-----------------------+
```

---

## 2. Watch History vs. Watch Progress Decoupling

| Dimension | Watch Progress (`watch_progress`) | Watch History (`watch_history`) |
| :--- | :--- | :--- |
| **Product Purpose** | Resume playback, bookmark cursor, continue watching | Chronological activity feed ("Recently Watched") |
| **Query Pattern** | Lookup by `(user_id, content_id)` for exact playback point | Paginated query by `user_id ORDER BY last_watched_at DESC` |
| **Deletion Semantics** | Updated on seeking/re-start; cleared on finishing if desired | Explicitly removable by user (`DELETE /history/{id}` or clear all) |
| **Lifecycle Trigger** | Any playback session start / progress update | Meaningful playback activity (progress, heartbeat, session end) |
| **Storage Table** | `watch_progress` (Flyway V13) | `watch_history` (Flyway V14) |

---

## 3. Database Schema & Indexes (Flyway V14)

### `watch_history` Table
- `id`: BIGSERIAL PRIMARY KEY
- `user_id`: BIGINT NOT NULL REFERENCES `users(id)` ON DELETE CASCADE
- `content_id`: BIGINT NOT NULL REFERENCES `content(id)` ON DELETE CASCADE
- `playback_session_id`: VARCHAR(64) (References last active session ID without cascade delete)
- `watched_seconds`: INTEGER NOT NULL DEFAULT 0 (highest/current playback point)
- `duration_seconds`: INTEGER NOT NULL DEFAULT 0 (media runtime)
- `completion_percentage`: DOUBLE PRECISION NOT NULL DEFAULT 0.0
- `completed`: BOOLEAN NOT NULL DEFAULT FALSE (95% completion threshold)
- `device_id`: VARCHAR(255) (e.g. `iphone-15-pro-uuid`, `pixel-8-uuid`, browser cookie)
- `platform`: VARCHAR(32) NOT NULL DEFAULT 'WEB' (`IOS`, `ANDROID`, `WEB`, `TV`)
- `first_watched_at`: TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
- `last_watched_at`: TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
- `created_at`, `updated_at`, `version`: Optimistic locking and audit timestamps
- **Unique Constraint**: `uq_watch_history_user_content (user_id, content_id)`

### Performance Indexes
- `idx_watch_history_user_last_watched ON watch_history (user_id, last_watched_at DESC)`: Powers high-speed pagination of the user's recently watched feed.
- `idx_watch_history_content_id ON watch_history (content_id)`: Foreign key index for cascade joins and catalog integrity.
- `idx_watch_history_session_id ON watch_history (playback_session_id)`: Diagnostic lookup index.

---

## 4. History Creation & Update Rules

1. **Meaningful Viewing Trigger**:
   - Merely calling `POST /playback/sessions` to get a playback URL does **NOT** immediately create a history entry if `resumePosition == 0`.
   - History is created/updated once meaningful playback occurs:
     - `POST /playback/sessions/{sessionId}/progress`
     - `POST /playback/sessions/{sessionId}/heartbeat` (with position)
     - `POST /playback/sessions/{sessionId}/end` (with position)
2. **Rewatching & Bubbling to Top**:
   - When a user watches Movie A on Jan 1, and rewatches Movie A on Jan 15, `last_watched_at` is updated to `now()`, `watched_seconds` updated to current position, and Movie A naturally bubbles to the top of the "Recently Watched" list without duplicate entries.
3. **Multi-Device Support**:
   - Simultaneous viewing across iPhone, Android, and Web updates distinct titles without collisions. When a user switches devices for the same title, `platform` and `device_id` update cleanly to reflect the latest interaction.
4. **Completion Rule**:
   - Inherits `completionThresholdPercent: 95.0%` from Phase 6.1 configuration (`communityott.playback.completion-threshold-percent`).

---

## 5. Endpoints & API Contract

### 1. `GET /api/v1/users/me/history`
Retrieves a paginated list of recently watched content items for the authenticated user, ordered newest first.
- **Parameters**: `page` (default 0), `size` (default 20, max 50).
- **Response (200 OK)**:
```json
{
  "success": true,
  "message": "Watch history retrieved successfully",
  "data": {
    "content": [
      {
        "id": 1,
        "contentId": 101,
        "title": "Telugu Handloom Weaving Traditions",
        "subtitle": "A cultural heritage journey",
        "thumbnailUrl": "https://media.communityott.com/thumbs/weaving.jpg",
        "bannerUrl": "https://media.communityott.com/banners/weaving.jpg",
        "contentType": "DOCUMENTARY",
        "contentStatus": "PUBLISHED",
        "contentAvailable": true,
        "durationSeconds": 3600,
        "watchedSeconds": 1800,
        "completionPercentage": 50.0,
        "completed": false,
        "platform": "IOS",
        "deviceId": "iphone-15-pro",
        "firstWatchedAt": "2026-08-18T00:00:00Z",
        "lastWatchedAt": "2026-08-18T00:30:00Z"
      }
    ],
    "pageable": { ... },
    "totalElements": 1,
    "totalPages": 1,
    "last": true
  }
}
```

### 2. `DELETE /api/v1/users/me/history/{contentId}`
Removes a specific content item from the authenticated user's watch history. Idempotent.
- **Response (200 OK)**:
```json
{
  "success": true,
  "message": "Watch history item removed successfully",
  "data": null
}
```

### 3. `DELETE /api/v1/users/me/history`
Clears all watch history for the authenticated user account. Idempotent.
- **Response (200 OK)**:
```json
{
  "success": true,
  "message": "Watch history cleared successfully",
  "data": null
}
```

---

## 6. Security & Privacy Controls

- **Identity Derived from JWT**: `userId` is strictly resolved from `@AuthenticationPrincipal CommunityOttPrincipal`. Clients cannot pass `userId` in query parameters or request bodies.
- **Strict Data Isolation**: User A cannot view, modify, or delete User B's watch history.
- **Privacy & GDPR Compliance**: Full account history clear (`DELETE /api/v1/users/me/history`) permanently deletes history records in PostgreSQL without residual orphan records.
- **Graceful Handling of Archived Content**: If a watched content item is subsequently archived or deleted, `contentAvailable: false` is returned in the DTO without throwing 500 errors.

---

## 7. Verification & Test Suite Summary

- **Total Backend Tests**: **297 passed** (0 failures, 0 errors, 0 skipped).
- **Test Suite**: `WatchHistoryTest.java` (14 comprehensive integration tests covering pagination, multi-device, rewatch, completion, single item delete, clear all, unauthenticated 401, and cross-user isolation).
- **Flyway & Hibernate**: `V14__create_watch_history_schema.sql` validated with `ddl-auto=validate`.

---

## 8. Deferred Work (Strict Scope Boundaries)
- Continue Watching aggregation API (Phase 6.3 / 6.x).
- Recommendations & Personalized Feed Engine.
- Manager & Viewer Retention Analytics Dashboards.
- DRM (FairPlay / Widevine).
