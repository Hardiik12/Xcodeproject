# Phase 5.1: User Profile + OTT Content Catalog Foundation Walkthrough

## 1. Executive Summary & Architecture
Phase 5.1 establishes the domain, persistence, service, and security foundations for the **CommunityOTT Content Platform**:
- **User Profile Subsystem**: Decouples authentication accounts (`User`) from viewing identities (`Profile`), allowing multiple personalized viewing personas per account without modifying user identity records.
- **Content Catalog Subsystem**: Introduces a high-performance content catalog metadata foundation with full lifecycle state tracking (`DRAFT` → `READY` → `PUBLISHED`).
- **Catalog Security**: Public/consumer discovery endpoints strictly return `PUBLISHED` content only, while administrative operations are guarded by Spring Security Method Security (`@PreAuthorize`) and existing RBAC permissions.

---

## 2. Architectural Separation: User vs Profile
```
┌─────────────────────────────────────────────────────────────┐
│                       USER (Account)                        │
│   • id, email, phone, status (ACTIVE/SUSPENDED/DELETED)     │
│   • Security Principal & Auth Credential Holder             │
└──────────────────────────────┬──────────────────────────────┘
                               │ 1 : N (One User to Many Profiles)
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                      PROFILE (Viewing)                      │
│   • id, userId, displayName, avatarUrl, preferredLanguage   │
│   • isDefault (boolean)                                     │
│   • Viewing persona for recommendations, continue watching  │
└─────────────────────────────────────────────────────────────┘
```
### Why Decouple Profile from User?
1. **Multi-User Households**: Multiple family members can maintain independent watchlist, language preferences, and viewing history under one shared subscription/account.
2. **Identity Isolation**: Modifying display name or preferred language on an OTT profile does not mutate the core login credentials or KYC identity of the account holder.
3. **Future Extensibility**: Seamlessly accommodates Kids profiles, profile locking PINs, and parental maturity ratings without database schema redesigns.

---

## 3. Content Domain Model & Types

### Content Entity Structure
| Field | Type | Description |
| :--- | :--- | :--- |
| `id` | `Long` (PK) | Unique content identifier |
| `title` | `String` (255) | Content title |
| `description` | `TEXT` | Plot synopsis / cultural context |
| `contentType` | `ContentType` | `MOVIE`, `DOCUMENTARY`, `SERIES`, `EPISODE` |
| `releaseDate` | `LocalDate` | Original release date |
| `durationSeconds` | `Integer` | Total runtime in seconds |
| `ageRating` | `AgeRating` | `ALL`, `U`, `UA_7_PLUS`, `UA_13_PLUS`, `UA_16_PLUS`, `A` |
| `status` | `ContentStatus` | `DRAFT`, `UPLOADING`, `PROCESSING`, `READY`, `PUBLISHED`, `UNPUBLISHED`, `ARCHIVED`, `FAILED` |
| `thumbnailUrl` | `String` (500) | Vertical poster image URL |
| `bannerUrl` | `String` (500) | Horizontal hero banner URL |
| `isFeatured` | `boolean` | Flag for top hero carousel placement |
| `createdBy` | `Long` | User ID of content creator (Audit) |
| `updatedBy` | `Long` | User ID of last modifier (Audit) |
| `createdAt` / `updatedAt` | `Instant` | Timestamps with automatic JPA auditing |

---

## 4. Content Lifecycle State Machine
```
   ┌──────────┐
   │  DRAFT   │ ◄───────────────────────────┐
   └────┬─────┘                             │ (Restore)
        │                                   │
        ├───────────────┬──────────────┐    │
        ▼               ▼              ▼    │
  ┌───────────┐   ┌───────────┐   ┌─────────┴──┐
  │ UPLOADING │   │   READY   │   │  ARCHIVED  │
  └─────┬─────┘   └─────┬─────┘   └─────────▲──┘
        │               │                   │
        ▼               ▼                   │
  ┌───────────┐   ┌───────────┐             │
  │PROCESSING │   │ PUBLISHED │ ────────────┤
  └─────┬─────┘   └─────┬─────┘             │
        │               │                   │
        ├─────────┐     ▼                   │
        ▼         │ ┌───────────┐           │
    ┌───────┐     │ │UNPUBLISHED│ ──────────┤
    │ READY │     │ └─────┬─────┘           │
    └───────┘     │       │ (Re-publish)    │
                  ▼       └─────────────────┘
              ┌────────┐
              │ FAILED │
              └────────┘
```
### Valid State Transitions
* **`DRAFT`** → `UPLOADING`, `READY`, `PUBLISHED`, `ARCHIVED`
* **`UPLOADING`** → `PROCESSING`, `FAILED`, `ARCHIVED`
* **`PROCESSING`** → `READY`, `FAILED`, `ARCHIVED`
* **`READY`** → `PUBLISHED`, `DRAFT`, `ARCHIVED`
* **`PUBLISHED`** → `UNPUBLISHED`, `ARCHIVED`
* **`UNPUBLISHED`** → `PUBLISHED`, `ARCHIVED`
* **`FAILED`** → `DRAFT`, `UPLOADING`, `ARCHIVED`
* **`ARCHIVED`** → `DRAFT`

---

## 5. Database Schema & Flyway Migration `V6`
The schema is managed by [`V6__create_profile_and_content_schema.sql`](file:///Users/hardik/Documents/IOS/CommunityOTT/backend/src/main/resources/db/migration/V6__create_profile_and_content_schema.sql):

```sql
CREATE TABLE IF NOT EXISTS profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    display_name VARCHAR(100) NOT NULL,
    avatar_url VARCHAR(500),
    preferred_language VARCHAR(20) NOT NULL DEFAULT 'en',
    is_default BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_profiles_user_id ON profiles(user_id);
CREATE INDEX IF NOT EXISTS idx_profiles_user_default ON profiles(user_id, is_default);

CREATE TABLE IF NOT EXISTS content (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    content_type VARCHAR(50) NOT NULL,
    release_date DATE,
    duration_seconds INTEGER,
    age_rating VARCHAR(20) NOT NULL DEFAULT 'U',
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    thumbnail_url VARCHAR(500),
    banner_url VARCHAR(500),
    is_featured BOOLEAN NOT NULL DEFAULT false,
    created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    updated_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_content_status ON content(status);
CREATE INDEX IF NOT EXISTS idx_content_content_type ON content(content_type);
CREATE INDEX IF NOT EXISTS idx_content_release_date ON content(release_date);
CREATE INDEX IF NOT EXISTS idx_content_created_at ON content(created_at);
CREATE INDEX IF NOT EXISTS idx_content_status_created_at ON content(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_content_status_content_type ON content(status, content_type, created_at DESC);
```

---

## 6. REST API Endpoints

### 6.1 User Profiles API (`/api/v1/profiles`)
| Method | Endpoint | Description | Security |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/profiles` | Create a new viewing persona | Authenticated User |
| `GET` | `/api/v1/profiles` | List all profiles for authenticated user | Authenticated User |
| `GET` | `/api/v1/profiles/{id}` | Get specific viewing profile | Profile Owner |
| `PUT` | `/api/v1/profiles/{id}` | Update profile details | Profile Owner |
| `DELETE` | `/api/v1/profiles/{id}` | Delete profile and reassign default | Profile Owner |

### 6.2 Public / Consumer Catalog API (`/api/v1/content`)
| Method | Endpoint | Description | Security |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/content` | Paginated feed of `PUBLISHED` content | `CONTENT_VIEW` permission |
| `GET` | `/api/v1/content/{id}` | Full metadata for a `PUBLISHED` content | `CONTENT_VIEW` permission |
| `GET` | `/api/v1/content/featured` | List of featured `PUBLISHED` hero items | `CONTENT_VIEW` permission |

### 6.3 Admin Content Management API (`/api/v1/admin/content`)
| Method | Endpoint | Description | Required RBAC Permission |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/admin/content` | Create new item in `DRAFT` | `CONTENT_CREATE` |
| `GET` | `/api/v1/admin/content` | List all items with status & type filter | `CONTENT_VIEW` |
| `GET` | `/api/v1/admin/content/{id}` | Get item details regardless of status | `CONTENT_VIEW` |
| `PUT` | `/api/v1/admin/content/{id}` | Update item metadata | `CONTENT_UPDATE` |
| `PATCH` | `/api/v1/admin/content/{id}/status` | Transition lifecycle state | `CONTENT_PUBLISH` or `CONTENT_UPDATE` |
| `DELETE` | `/api/v1/admin/content/{id}` | Logically archive item (`ARCHIVED`) | `CONTENT_DELETE` or `CONTENT_ARCHIVE` |

---

## 7. Security Decisions & RBAC Enforcement
1. **Public Catalog Filtering**: Implemented strictly in the database layer via `contentRepository.findByStatus(ContentStatus.PUBLISHED, pageable)` rather than filtering in Java memory.
2. **Access Isolation**: Profile endpoints enforce user ownership (`findByIdAndUserId(id, principal.getUserId())`). Unauthorized cross-tenant attempts return `404 PROFILE_NOT_FOUND`.
3. **Auditing**: Admin IDs are extracted directly from `SecurityContextHolder` (`CommunityOttPrincipal`) and recorded in `createdBy` and `updatedBy`. Clients cannot tamper with audit fields.

---

## 8. Relationship to Future Video Pipeline
```
Phase 5.1 (Current)           Phase 5.2/5.3 (Future)               Phase 6 (Future)
┌─────────────────┐           ┌──────────────────┐           ┌────────────────────┐
│  Content Entity │           │   Video Assets   │           │    HLS Streaming   │
│  (Metadata DB)  │ ◄──────── │  (MinIO Storage) │ ◄──────── │ (FFmpeg Renditions │
│                 │           │  Raw Uploads     │           │   1080p, 720p, 480p│
│                 │           │  Processing Job  │           │   CDN Master m3u8) │
└─────────────────┘           └──────────────────┘           └────────────────────┘
```
1. **Phase 5.1**: Defines metadata boundaries, title, description, duration, and publication status.
2. **Future Phase 5.2/5.3**: Links `Content` to raw video assets stored in MinIO object storage.
3. **Future Phase 6**: Video workers transcode raw video into multi-bitrate HLS streams, update the content status from `PROCESSING` to `READY`, and populate playable stream URLs.

---

## 9. Verification & Test Suite
* **`ProfileManagementTest.java`**: 6 test cases verifying creation, auto-default assignment, multi-profile isolation, cross-user security, update, and deletion re-balancing.
* **`ContentCatalogTest.java`**: 10 test cases verifying admin content creation, RBAC authorization, valid/invalid state transitions, archive behavior, public catalog status filtering, and featured rails.
* **Total Backend Tests Passing**: **146 / 146**
* **Build Status**: **BUILD SUCCESS**
