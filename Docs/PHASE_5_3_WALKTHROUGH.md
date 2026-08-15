# CommunityOTT Backend — Phase 5.3 Walkthrough
## Admin + Content Manager Content Management System (CMS) & Lifecycle

---

## 1. Executive Summary

Phase 5.3 implements the administrative Content Management System (CMS) and lifecycle management engine for CommunityOTT. CommunityOTT is a premium OTT streaming platform; normal users consume published content, while authorized administrative roles manage content metadata and lifecycle transitions.

This phase establishes:
- Strict lifecycle state transitions (`DRAFT` → `UPLOADING` → `PROCESSING` → `READY` → `PUBLISHED`, `PUBLISHED` → `UNPUBLISHED` / `ARCHIVED`, `PROCESSING` → `FAILED` → retry).
- Publishing prerequisites enforcement via [`ContentPublishabilityChecker`](file:///Users/hardik/Documents/IOS/CommunityOTT/backend/src/main/java/com/communityott/content/service/ContentPublishabilityChecker.java).
- Optimistic locking concurrency control via `@Version` and Flyway migration `V8`.
- Administrative metadata management, state transitions, and catalog status breakdown summary.
- Comprehensive security and role separation (`SUPER_ADMIN`, `CONTENT_MANAGER`, `MANAGER`, `USER`).

All **186 automated tests** across all backend phases (Phase 1 through Phase 5.3) pass with 100% success (`BUILD SUCCESS`).

---

## 2. Architecture & Design Decisions

### 2.1 Content Lifecycle State Machine
Content lifecycle transitions are governed strictly by [`ContentLifecycleService`](file:///Users/hardik/Documents/IOS/CommunityOTT/backend/src/main/java/com/communityott/content/service/ContentLifecycleService.java) rather than ad-hoc controller updates.

```
       ┌─────────┐
       │  DRAFT  │
       └────┬────┘
            │ (Initiate Upload)
            ▼
       ┌───────────┐
       │ UPLOADING │
       └────┬──────┘
            │ (Upload Complete)
            ▼
       ┌────────────┐     Processing Fails     ┌────────┐
       │ PROCESSING ├─────────────────────────►│ FAILED │
       └────┬───────┘                          └───┬────┘
            │ (Transcoding Complete)               │ (Retry Processing)
            ▼                                      ▼
       ┌─────────┐                            ┌────────────┐
       │  READY  │◄───────────────────────────┤ PROCESSING │
       └────┬────┘                            └────────────┘
            │ (Publish Prereqs Pass)
            ▼
       ┌───────────┐        Unpublish         ┌─────────────┐
       │ PUBLISHED ├─────────────────────────►│ UNPUBLISHED │
       └────┬──────┘                          └─────┬───────┘
            │                                       │
            │ Archive                               │ Archive
            ▼                                       ▼
       ┌────────────────────────────────────────────────────┐
       │                      ARCHIVED                      │
       └────────────────────────────────────────────────────┘
```

#### Valid State Transitions:
- `DRAFT` → `UPLOADING`
- `UPLOADING` → `PROCESSING`, `FAILED`
- `PROCESSING` → `READY`, `FAILED`
- `FAILED` → `PROCESSING`, `UPLOADING`, `ARCHIVED`
- `READY` → `PUBLISHED`, `ARCHIVED`, `UPLOADING`
- `PUBLISHED` → `UNPUBLISHED`, `ARCHIVED`
- `UNPUBLISHED` → `PUBLISHED`, `ARCHIVED`
- `ARCHIVED` → (Terminal state; no automated republish)

#### Invalid Transitions:
- `DRAFT` → `PUBLISHED`: Rejected with `ContentNotPublishableException` / `InvalidContentStateTransitionException` (content cannot be published without completed video processing).
- `PROCESSING` → `PUBLISHED`: Rejected.
- `FAILED` → `PUBLISHED`: Rejected.
- `ARCHIVED` → `PUBLISHED`: Rejected.
- `DRAFT` → `ARCHIVED`: Rejected.

### 2.2 Publishability Checker Abstraction
The [`ContentPublishabilityChecker`](file:///Users/hardik/Documents/IOS/CommunityOTT/backend/src/main/java/com/communityott/content/service/ContentPublishabilityChecker.java) interface decouples the CMS layer from future video transcoding and HLS readiness pipelines.

[`DefaultContentPublishabilityChecker`](file:///Users/hardik/Documents/IOS/CommunityOTT/backend/src/main/java/com/communityott/content/service/DefaultContentPublishabilityChecker.java) validates:
1. Status is `READY` or `UNPUBLISHED`.
2. Title is non-blank.
3. Duration is positive (> 0).
4. ContentType is specified.

In Phase 5.4/5.5, video asset validation (e.g., verifying presence of HLS master manifest and renditions) will plug directly into this checker without modifying controller contracts.

### 2.3 Optimistic Locking Concurrency Strategy
- To prevent lost updates when multiple content managers or administrators edit catalog metadata or trigger publishing actions simultaneously, an optimistic locking `version` column is added to `content` (`@Version private Long version = 0L;`).
- When a stale version is submitted or concurrent conflict occurs, Hibernate raises `ObjectOptimisticLockingFailureException`, which [`GlobalExceptionHandler`](file:///Users/hardik/Documents/IOS/CommunityOTT/backend/src/main/java/com/communityott/common/exception/GlobalExceptionHandler.java) translates into HTTP 409 `CONTENT_VERSION_CONFLICT`.

### 2.4 Server-Controlled Audit Immutability
- Creation (`POST /api/v1/admin/content`) and updates (`PUT /api/v1/admin/content/{id}`) derive `createdBy` and `updatedBy` strictly from the authenticated principal (`CommunityOttPrincipal.getUserId()`).
- Any client-supplied values for `id`, `status`, `version`, `createdBy`, `createdAt`, `updatedAt` are ignored.
- All state transitions record `updatedBy = principal.getUserId()` and `updatedAt = Instant.now()`.

### 2.5 Catalog Status Summary Breakdown
Administrative dashboards can query `GET /api/v1/admin/content/summary` to retrieve exact catalog health counts across all lifecycle states (`draft`, `uploading`, `processing`, `ready`, `published`, `unpublished`, `failed`, `archived`, `total`).

---

## 3. Database Schema Migration (`V8__add_content_version_and_lifecycle_enhancements.sql`)

```sql
-- Phase 5.3: Add optimistic locking version column and lifecycle indexes to content table
ALTER TABLE content ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX idx_content_status ON content(status);
CREATE INDEX idx_content_created_by ON content(created_by);
```

---

## 4. Administrative REST API Specification

| Method | Endpoint | Description | Required Permission |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/admin/content` | Create content (defaults strictly to `DRAFT`) | `CONTENT_CREATE` |
| `GET` | `/api/v1/admin/content` | List paginated admin content with multi-status filters | `CONTENT_VIEW` |
| `GET` | `/api/v1/admin/content/{id}` | Get full admin content details including audit metadata | `CONTENT_VIEW` |
| `PUT` | `/api/v1/admin/content/{id}` | Update content catalog metadata | `CONTENT_UPDATE` |
| `PUT` | `/api/v1/admin/content/{id}/metadata` | Update taxonomy, tags, categories, languages | `CONTENT_METADATA_UPDATE` / `CONTENT_UPDATE` |
| `GET` | `/api/v1/admin/content/summary` | Get catalog lifecycle status breakdown counts | `CONTENT_VIEW` |
| `POST` | `/api/v1/admin/content/{id}/publish` | Publish `READY` content to public consumer feeds | `CONTENT_PUBLISH` |
| `POST` | `/api/v1/admin/content/{id}/unpublish` | Unpublish content (hides immediately from consumers) | `CONTENT_PUBLISH` |
| `POST` | `/api/v1/admin/content/{id}/archive` | Archive content (logical deletion preserving history) | `CONTENT_ARCHIVE` / `CONTENT_DELETE` |
| `POST` | `/api/v1/admin/content/{id}/retry-processing` | Retry failed processing (`FAILED` → `PROCESSING`) | `VIDEO_RETRY` / `CONTENT_UPDATE` |
| `POST` | `/api/v1/admin/content/{id}/transition` | Generic validated state machine transition | `CONTENT_UPDATE` / `CONTENT_PUBLISH` |
| `DELETE`| `/api/v1/admin/content/{id}` | Logical archive endpoint mapping | `CONTENT_ARCHIVE` / `CONTENT_DELETE` |

---

## 5. Security & RBAC Matrix

| Role | Browse Published | Create Content | Edit Metadata | Publish / Unpublish | Retry Processing | Archive |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **USER** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **CONTENT_MANAGER** | ✅ | ✅ | ✅ | ❌ | ✅ | ❌ |
| **MANAGER** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **SUPER_ADMIN** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |

- **Strict Visibility Boundary**: Public consumer catalog (`GET /api/v1/content`) queries strictly filter `WHERE status = 'PUBLISHED'`. Inactive lifecycle states (`DRAFT`, `UPLOADING`, `PROCESSING`, `READY`, `FAILED`, `UNPUBLISHED`, `ARCHIVED`) are completely inaccessible to normal consumers.

---

## 6. Test Suite & Verification Results

### Test Execution Summary
- **Total Tests**: **186 tests run, 0 failures, 0 errors, 0 skipped** (`BUILD SUCCESS`).
- **Phase 5.3 Test Suite ([`ContentLifecycleManagementTest.java`](file:///Users/hardik/Documents/IOS/CommunityOTT/backend/src/test/java/com/communityott/ContentLifecycleManagementTest.java))**:
  1. `test01_createContentDefaultsToDraft`: Verifies content creation defaults to `DRAFT` with version 0.
  2. `test02_userCannotCreateContent`: Verifies `USER` role is rejected with 403 Forbidden.
  3. `test03_invalidCreationRejected`: Verifies blank title / invalid inputs return 400 Bad Request.
  4. `test04_updateContentMetadata`: Verifies authorized update preserves `createdBy` and updates audit fields.
  5. `test05_userCannotUpdateMetadata`: Verifies unauthorized update returns 403 Forbidden.
  6. `test06_contentStatusSummaryBreakdown`: Verifies status counts returned by `/summary`.
  7. `test07_adminContentList`: Verifies status filtering on admin list.
  8. `test08_validLifecyclePipeline`: Verifies sequential `DRAFT` → `UPLOADING` → `PROCESSING` → `READY`.
  9. `test09_processingFailureAndRetry`: Verifies `PROCESSING` → `FAILED` → `retry-processing` → `PROCESSING`.
  10. `test10_illegalTransitionsRejected`: Verifies illegal transitions (`DRAFT -> PUBLISHED`, `DRAFT -> ARCHIVED`) return 400 Bad Request.
  11. `test11_publishReadyContent`: Verifies publishing changes public catalog visibility.
  12. `test12_contentManagerCannotPublishWithoutPermission`: Verifies content manager lacking `CONTENT_PUBLISH` gets 403 Forbidden.
  13. `test13_unpublishHidesFromPublicCatalog`: Verifies `UNPUBLISHED` content immediately returns 404 on consumer endpoint.
  14. `test14_archiveContent`: Verifies `ARCHIVED` content is hidden from consumer catalog.
  15. `test15_optimisticLockingConflict`: Verifies concurrent modifications on stale versions throw optimistic locking failure.

---

## 7. Future Video Pipeline Integration Roadmap

Phase 5.3 prepares the backend for:
- **Phase 5.4**: Video Upload Staging & MinIO Object Storage integration.
- **Phase 5.5**: Async Video Transcoding Worker (FFmpeg / HLS packaging).
- **Phase 5.6**: Video Renditions, Master Playlists, Subtitle Streams & CDN edge caching.
- **Phase 5.7**: Secure Playback API & Token-authenticated HLS streaming.
