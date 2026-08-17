# CommunityOTT Phase 6.3: Continue Watching Feature Walkthrough

## 1. Executive Summary

Phase 6.3 implements the **Netflix-style Continue Watching** feature for the CommunityOTT monolithic Spring Boot OTT backend.
Continue Watching is a **derived consumer feature** that discovers and surfaces media items the authenticated user has begun watching (`position_seconds > 0`) but has not yet completed (`completed = false` and `completion_percentage < 95.0%`), for content that is **currently playable** on the platform.

---

## 2. Architectural Overview

```
                                      +------------------------------------+
                                      |    CLIENT (iOS / Android / Web)    |
                                      +------------------------------------+
                                                         |
                                               1. GET /api/v1/users/me/continue-watching
                                                  (Bearer JWT / Auth Session)
                                                         v
                                      +------------------------------------+
                                      |     ContinueWatchingController     |
                                      | (Derives userId from SecurityCtx)  |
                                      +------------------------------------+
                                                         |
                                                         v
                                      +------------------------------------+
                                      |      ContinueWatchingService       |
                                      | - Page size bounded to max 50      |
                                      | - Derives remainingSeconds         |
                                      | - Computes exact completionPercent |
                                      +------------------------------------+
                                                         |
                                                         v
                                      +------------------------------------+
                                      |      WatchProgressRepository       |
                                      |  (JOIN FETCH Content + VideoAsset  |
                                      |   + EXISTS READY VideoAsset & HLS) |
                                      +------------------------------------+
                                                         |
                                                         v
                                      +------------------------------------+
                                      |         PostgreSQL Database        |
                                      | - watch_progress (V13 / V15 Index) |
                                      | - content (Status: PUBLISHED)      |
                                      | - video_assets (Status: READY)     |
                                      | - video_hls_packages (READY)       |
                                      +------------------------------------+
```

---

## 3. Why Continue Watching is a Derived Feature

1. **No Redundant Database**: Rather than maintaining a separate table that duplicates watch states, Continue Watching is derived from `WatchProgress` (Phase 6.1) combined with real-time `Content` availability and `VideoHlsPackage` readiness.
2. **Instant Consistency**: Whenever a viewer records progress via heartbeats or progress syncs, or whenever an admin unpublishes a title, Continue Watching reflects the change immediately without requiring background synchronizer jobs.
3. **Data Integrity**: Deleting or completing an item directly updates `watch_progress`, immediately removing it from the user's Continue Watching feed.

---

## 4. Relationship with WatchProgress & WatchHistory

| Dimension | WatchProgress (Phase 6.1) | WatchHistory (Phase 6.2) | Continue Watching (Phase 6.3) |
| :--- | :--- | :--- | :--- |
| **Purpose** | Authoritative playback bookmark cursor | Complete chronological audit log of viewing | Curated homepage feed of active, in-progress titles |
| **Database Table** | `watch_progress` | `watch_history` | **Derived Query** (No separate table) |
| **Completion Handling**| Retains `completed = true` when finished | Records finished views (`completed = true`) | **Excludes completed items** (`completed = false`) |
| **Availability Filtering** | Independent of status | Shows archived/deleted as historical records | **Strictly filters only currently playable content** |
| **Client Usage** | Player resume upon playback launch | "Recently Watched" profile history screen | Top Carousel / Row on OTT Home Feed |

---

## 5. Content Availability & Playability Filtering

A Continue Watching item is returned **only if all of the following conditions are met**:
1. `wp.user.id = :userId` (belongs to authenticated user).
2. `wp.completed = false` (not completed).
3. `wp.positionSeconds > 0` (playback was actually started).
4. `c.status = 'PUBLISHED'` (content is live; `DRAFT`, `PROCESSING`, `FAILED`, `ARCHIVED` are excluded).
5. At least one `VideoAsset` for the content has `status = 'READY'`.
6. That ready `VideoAsset` has an associated `VideoHlsPackage` with `status = 'READY'`.

If an asset is being re-transcoded or a title is archived, it disappears from Continue Watching automatically without throwing 500 errors.

---

## 6. Completion Rules & Rewatch Behavior

- **Completion Threshold**: Reuses the centralized 95.0% threshold from `PlaybackProperties` (Phase 6.1). When `positionSeconds / durationSeconds >= 0.95`, the item is marked `completed = true` and dropped from Continue Watching.
- **Rewatch Behavior**: If a user finishes a movie (96%) and later restarts it from the beginning (e.g. 120s), `WatchProgressService` resets `completed = false` and updates `positionSeconds = 120s`. The movie automatically re-enters Continue Watching.

---

## 7. Remaining Time & Resume Calculations

For each item in the response, `ContinueWatchingItemResponse` calculates:
1. `durationSeconds`: Authoritative duration from `WatchProgress` or `Content`.
2. `positionSeconds`: Authoritative bookmark cursor in seconds.
3. `completionPercentage`: `(positionSeconds / durationSeconds) * 100.0` (clamped between 0.0% and 100.0%).
4. `remainingSeconds`: `Math.max(0, durationSeconds - positionSeconds)` (guaranteed non-negative).

---

## 8. Pagination & Ordering

- **Endpoint**: `GET /api/v1/users/me/continue-watching`
- **Default Pagination**: Page 0, Size 20.
- **Maximum Page Size**: Hard-bounded to 50 in `ContinueWatchingService` to prevent denial-of-service memory pressure.
- **Ordering**: Strict `lastWatchedAt DESC` (the content watched most recently is returned first).

---

## 9. Security & User Isolation

- **Authentication**: Requires valid JWT Bearer token or development `X-User-Id` header.
- **Authorization**: Consumers access only their own data. `userId` is extracted strictly from `@AuthenticationPrincipal CommunityOttPrincipal`.
- **Zero URL Leakage**: Continue Watching returns metadata and bookmarks only. It does **not** generate premature signed HLS URLs, reducing URL expiration bugs and CDN signature overhead.

---

## 10. Database Schema & Index Strategy

Flyway migration `V15__create_continue_watching_index.sql`:
```sql
CREATE INDEX IF NOT EXISTS idx_watch_progress_continue_watching
    ON watch_progress (user_id, completed, position_seconds, last_watched_at DESC);
```

This compound index allows PostgreSQL to satisfy the filter `WHERE user_id = ? AND completed = false AND position_seconds > 0` and the sort `ORDER BY last_watched_at DESC` in a single index scan.

---

## 11. Caching Strategy

- **PostgreSQL Source of Truth**: PostgreSQL is the single authoritative source of truth for Continue Watching.
- **Redis Decision**: Redis caching is intentionally avoided for this derived query in Phase 6.3. The query executes in sub-millisecond time via the V15 composite index, avoiding cache invalidation complexity across multi-device progress syncs.

---

## 12. Client Integration (iOS & Android)

### Step 1: Query Continue Watching on Home Screen Launch
```http
GET /api/v1/users/me/continue-watching?page=0&size=20 HTTP/1.1
Host: api.communityott.com
Authorization: Bearer <user_jwt_token>
```

### Step 2: Render Continue Watching Cards
The mobile UI displays cards with progress bars using `completionPercentage` and `remainingSeconds`:
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "contentId": 101,
        "title": "Weavers of Pochampally",
        "subtitle": "Culture & Heritage",
        "thumbnailUrl": "https://media.communityott.com/thumbs/101.jpg",
        "bannerUrl": "https://media.communityott.com/banners/101.jpg",
        "contentType": "DOCUMENTARY",
        "durationSeconds": 3600,
        "positionSeconds": 1800,
        "completionPercentage": 50.0,
        "remainingSeconds": 1800,
        "lastWatchedAt": "2026-08-18T01:10:00Z",
        "completed": false
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  },
  "message": "Continue watching retrieved successfully",
  "timestamp": "2026-08-18T01:16:00Z"
}
```

### Step 3: Tapping a Card to Resume
When the user taps the item, the app starts a session and passes `resumePositionSeconds` to the player:
```http
POST /api/v1/content/101/playback/sessions HTTP/1.1
Host: api.communityott.com
Authorization: Bearer <user_jwt_token>
Content-Type: application/json

{
  "platform": "IOS",
  "deviceId": "iPhone16Pro-Max"
}
```
The response returns `resumePositionSeconds: 1800` and `playbackUrl`, allowing AVPlayer / ExoPlayer to seek immediately to 1800s upon load.

---

## 13. Scope Status: Implemented vs. Deferred

### Implemented in Phase 6.3:
- [x] Flyway Migration `V15__create_continue_watching_index.sql`
- [x] Derived Continue Watching query joining `WatchProgress`, `Content`, `VideoAsset`, and `VideoHlsPackage`
- [x] Completion threshold filtering (95% rule from Phase 6.1)
- [x] Content availability and HLS readiness filtering
- [x] Non-negative remaining time & accurate completion percentage calculation
- [x] Chronological ordering (`lastWatchedAt DESC`)
- [x] Pagination with 50-item upper bound
- [x] Rewatch detection & resume handling
- [x] Multi-device user progress synchronization
- [x] Zero-URL generation security pattern
- [x] 14 unit & integration tests (`ContinueWatchingTest.java`)

### Deferred to Future Phases:
- **Series & Episode Hierarchy**: Episode-level next-up detection and season rollup (Deferred to Series module).
- **Manual "Remove from Continue Watching" UI Action**: Explicit item dismissal endpoint (Deferred).
- **QoE & Viewer Retention Analytics**: Aggregated viewer engagement dashboards (Deferred).
- **Recommendations Engine**: Machine learning personalized home recommendations (Deferred).
