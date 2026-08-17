# CommunityOTT Phase 6.4: Saved Content / My List / Favorites Walkthrough

## 1. Executive Summary

Phase 6.4 implements the **Saved Content (My List / Watchlist / Favorites)** system for the CommunityOTT monolithic Spring Boot backend.
This feature enables users to curate personal lists of movies, documentaries, and community content for later viewing. It operates with strict database uniqueness, user isolation, N+1 query prevention, and full architectural independence from watch progress, watch history, and continue watching streams.

---

## 2. Architectural Overview

```
                                    +------------------------------------+
                                    |    CLIENT (iOS / Android / Web)    |
                                    +------------------------------------+
                                       /              |               \
                1. POST /my-list/{id} /  2. GET /my-list\  3. GET /my-list/{id}
               (Add - Idempotent)    /   (Paginated)     \ (Check Status)
                                    v                 v   v
                                    +------------------------------------+
                                    |       SavedContentController       |
                                    | (Derives userId from SecurityCtx)  |
                                    +------------------------------------+
                                                      |
                                                      v
                                    +------------------------------------+
                                    |        SavedContentService         |
                                    | - Idempotent additions & deletions |
                                    | - Page size bounded to max 50      |
                                    | - Content availability evaluation  |
                                    +------------------------------------+
                                                      |
                                                      v
                                    +------------------------------------+
                                    |       SavedContentRepository       |
                                    |   (JOIN FETCH Content metadata)    |
                                    +------------------------------------+
                                                      |
                                                      v
                                    +------------------------------------+
                                    |        PostgreSQL Database         |
                                    | - saved_content (V16 Migration)    |
                                    | - UNIQUE (user_id, content_id)     |
                                    | - idx (user_id, saved_at DESC)     |
                                    +------------------------------------+
```

---

## 3. Four Core OTT Discovery Concepts Compared

| Dimension | Saved Content (Phase 6.4) | Watch Progress (Phase 6.1) | Watch History (Phase 6.2) | Continue Watching (Phase 6.3) |
| :--- | :--- | :--- | :--- | :--- |
| **User Intent** | "I want to watch this later" | "Where did I stop watching?" | "What did I watch in the past?" | "What active title should I resume?" |
| **Database Table** | `saved_content` | `watch_progress` | `watch_history` | **Derived Query** |
| **User Action** | Manual bookmark (+ My List) | Automated player heartbeats | Automated progress audit | Derived active playback state |
| **Lifecycle** | Persists until user removes it | Updated continuously per view | Retained until user clears history | Disappears once completed (>=95%) |
| **Coexistence** | Can be saved and in continue watching simultaneously | Bookmark cursor only | Chronological audit log | Resumable homepage carousel |

---

## 4. Database Schema & Flyway Migration (`V16`)

Flyway migration `V16__create_saved_content_schema.sql`:
```sql
CREATE TABLE IF NOT EXISTS saved_content (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    content_id BIGINT NOT NULL,
    saved_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_saved_content_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_saved_content_content FOREIGN KEY (content_id) REFERENCES content(id) ON DELETE CASCADE,
    CONSTRAINT uq_saved_content_user_content UNIQUE (user_id, content_id)
);

CREATE INDEX IF NOT EXISTS idx_saved_content_user_saved_at ON saved_content(user_id, saved_at DESC);
CREATE INDEX IF NOT EXISTS idx_saved_content_content_id ON saved_content(content_id);
```

- **Unique Constraint**: `uq_saved_content_user_content` prevents duplicate bookmark rows at the database level.
- **Index Optimization**: `idx_saved_content_user_saved_at` provides sub-millisecond retrieval sorted newest-first.

---

## 5. API Contracts & Endpoint Specifications

### 1. Add to My List
- **Method**: `POST /api/v1/users/me/my-list/{contentId}`
- **Behavior**: Idempotent. Validates content exists. If already present, returns existing saved entity without creating duplicates.
- **Response**: `200 OK` with `SavedContentResponse`.

### 2. Remove from My List
- **Method**: `DELETE /api/v1/users/me/my-list/{contentId}`
- **Behavior**: Idempotent. Removes the user-content bookmark.
- **Response**: `200 OK` with `ApiResponse<Void>`.

### 3. Check Saved Status
- **Method**: `GET /api/v1/users/me/my-list/{contentId}`
- **Behavior**: Fast boolean check for mobile detail screens ("+ My List" vs. "✓ In My List").
- **Response**: `200 OK` with `{"contentId": 101, "saved": true}`.

### 4. Get My List (Paginated)
- **Method**: `GET /api/v1/users/me/my-list?page=0&size=20`
- **Behavior**: Bounded pagination (max size 50), ordered by `savedAt DESC`. Single SQL query with `JOIN FETCH` to eliminate N+1 latency.
- **Response**:
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 12,
        "contentId": 101,
        "title": "Kalamkari Art Documentary",
        "subtitle": "Ancient textiles",
        "thumbnailUrl": "https://media.communityott.com/thumbs/kalamkari.jpg",
        "bannerUrl": "https://media.communityott.com/banners/kalamkari.jpg",
        "contentType": "DOCUMENTARY",
        "durationSeconds": 3600,
        "savedAt": "2026-08-18T01:30:00Z",
        "isPlayable": true,
        "availability": "AVAILABLE"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  },
  "message": "My List retrieved successfully"
}
```

---

## 6. Content Availability & Deleted Content Handling

- **Soft Unavailability**: If a saved movie transitions to `DRAFT` or `ARCHIVED`, the bookmark relation is preserved. In `SavedContentResponse`, `isPlayable: false` and `availability: "UNAVAILABLE"` are returned so the user interface can display an "Unavailable" badge without crashing.
- **Cascade on Hard Delete**: If an admin completely deletes a content entity, `ON DELETE CASCADE` purges the bookmark cleanly.

---

## 7. Security & User Isolation

1. **Authentication**: All endpoints require a valid JWT Bearer token. Unauthenticated requests receive `401 Unauthorized`.
2. **Contextual Ownership**: `userId` is strictly derived from Spring Security's `CommunityOttPrincipal`. No client-supplied user IDs are accepted.
3. **Cross-User Protection**: User A cannot read, add, or delete bookmarks belonging to User B.

---

## 8. Playback Integration & URL Security

- My List endpoints return metadata and IDs only. They **never generate premature signed media URLs**.
- When a user taps a card in My List, the mobile app invokes `POST /api/v1/content/{id}/playback/sessions`, enforcing tokenized HLS streaming rules, rate limits, and access policies.

---

## 9. Caching & Persistence Strategy

- **Authoritative Database**: PostgreSQL is the single source of truth.
- **Redis Decision**: Redis caching is omitted for this phase because the compound index `(user_id, saved_at DESC)` delivers sub-millisecond execution without risk of stale caches across mobile apps.

---

## 10. Scope Status: Implemented vs. Deferred

### Implemented in Phase 6.4:
- [x] Flyway Migration `V16__create_saved_content_schema.sql`
- [x] `SavedContent` entity with optimistic locking and unique constraint
- [x] `SavedContentRepository` with `JOIN FETCH` query for N+1 prevention
- [x] `SavedContentService` handling idempotent add, delete, check, and bounded pagination
- [x] `SavedContentController` (`POST`, `DELETE`, `GET /my-list`, `GET /my-list/{id}`)
- [x] Decoupling from Watch Progress, Watch History, and Continue Watching
- [x] Content availability and playability badges
- [x] 15 integration tests in `SavedContentTest.java`

### Deferred to Future Phases:
- **Custom User Playlists / Folders**: Custom user-created named playlists (Deferred).
- **Social List Sharing**: Sharing My List links with other community members (Deferred).
- **Offline Download Queue**: Storing download states (Deferred to Downloads module).
- **Personalized Recommendations from Saved Content**: AI recommendation weights from watchlist items (Deferred).
