# CommunityOTT Backend — Phase 5.2 Walkthrough
## Categories, Languages, Genres & Content Metadata Architecture

---

## 1. Executive Summary

Phase 5.2 establishes the taxonomic and metadata structure for the CommunityOTT platform. It extends the content model established in Phase 5.1 with structured categories, languages, normalized metadata, explicit many-to-many relationship join entities, dynamic database-level filtering using JPA Specifications, whitelisted safe sorting, and bounded database pagination.

All 171 automated tests across all phases (Phase 1 through Phase 5.2) pass with 100% success (`BUILD SUCCESS`).

---

## 2. Architectural Decisions

### 2.1 Category Architecture
- **Normalized Table**: `categories` stores distinct content verticals (e.g. `Documentary`, `History`, `Culture`, `Science`, `Technology`, `Nature & Wildlife`, `Cinema & Drama`).
- **Uniqueness & Slugs**: Both `name` and `slug` are enforced unique at the database level. Slugs are URL-safe and lowercased.
- **Lifecycle & Soft-Deactivation**: Categories support `active = true/false`. Deactivation preserves historical data integrity while removing inactive categories from consumer feeds.

### 2.2 Language Architecture
- **Normalized Table**: `languages` stores language metadata with unique ISO codes (`code`, e.g. `te`, `en`, `hi`, `ta`, `kn`, `ml`).
- **Original vs. Available Languages**:
  - `content.original_language_id`: Distinguishes the primary production language of the content.
  - `content_languages`: Explicit join table mapping available audio and subtitle translations.
- **Extensibility**: Designed to support global multi-language catalogs and future audio/subtitle track streams.

### 2.3 Genre / Tag Decision
- In OTT platforms, high-level verticals are represented by `Category` (e.g., `Documentary`, `History`), while specific themes are represented by `tags` (e.g. `weaving`, `temples`, `ikat`, `heritage`).
- Rather than over-engineering 4 separate tables with identical taxonomy functions, `Category` serves as the primary relational taxonomy and `tags` provides fast, flexible searchable descriptor tokens.

### 2.4 Relationship Design (Explicit Join Entities)
- Avoided blind `@ManyToMany` associations. Consistent with the RBAC design (`UserRole`, `RolePermission`), explicit join entities are used:
  - `ContentCategory` (`content_categories` table) with composite key `ContentCategoryId (contentId, categoryId)`
  - `ContentLanguage` (`content_languages` table) with composite key `ContentLanguageId (contentId, languageId)`
- **Referential Integrity**:
  - `content_id`: Foreign key with `ON DELETE CASCADE`.
  - `category_id` / `language_id`: Foreign keys with `ON DELETE RESTRICT` to guarantee that deleting a taxonomy record can never accidentally delete or orphan content.

### 2.5 Dynamic Filtering & Strict Publication Boundary
- Uses Spring Data `JpaSpecificationExecutor<Content>` and `ContentSpecification` to construct composable SQL predicates:
  - **Mandatory Consumer Constraint**: `content.status = 'PUBLISHED'`. Even if custom filters are applied, non-published content (`DRAFT`, `UPLOADING`, `PROCESSING`, `FAILED`, `ARCHIVED`, `UNPUBLISHED`) is never exposed to public consumer feeds.
  - Optional filters: `contentType`, `category` (slug or ID), `language` (code or ID), `ageRating`, and `search` keyword (ILIKE on title, subtitle, description, tags).

### 2.6 Whitelisted Sorting & Bounded Pagination
- `SortValidator` checks all incoming sort fields against a strict whitelist: `releaseDate`, `createdAt`, `title`, `durationSeconds`, `updatedAt`.
- Prevents unindexed sort abuses and SQL injection vectors.
- Page size is bounded between 1 and 100 (defaulting to 20).

---

## 3. Database Schema Migration (`V7__create_category_language_and_metadata_schema.sql`)

```sql
-- Categories
CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    slug VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_categories_slug ON categories(slug);
CREATE INDEX idx_categories_active ON categories(active);

-- Languages
CREATE TABLE languages (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    code VARCHAR(20) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_languages_code ON languages(code);
CREATE INDEX idx_languages_active ON languages(active);

-- Content Enhancements
ALTER TABLE content
    ADD COLUMN subtitle VARCHAR(255),
    ADD COLUMN short_description VARCHAR(500),
    ADD COLUMN country_of_origin VARCHAR(100),
    ADD COLUMN original_language_id BIGINT REFERENCES languages(id) ON DELETE SET NULL,
    ADD COLUMN tags VARCHAR(500);

-- Content Categories Join Table
CREATE TABLE content_categories (
    content_id BIGINT NOT NULL REFERENCES content(id) ON DELETE CASCADE,
    category_id BIGINT NOT NULL REFERENCES categories(id) ON DELETE RESTRICT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (content_id, category_id)
);

-- Content Languages Join Table
CREATE TABLE content_languages (
    content_id BIGINT NOT NULL REFERENCES content(id) ON DELETE CASCADE,
    language_id BIGINT NOT NULL REFERENCES languages(id) ON DELETE RESTRICT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (content_id, language_id)
);
```

---

## 4. API Endpoints

### 4.1 Public Consumer Catalog APIs
| Method | Endpoint | Description | Required Permission |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/content` | Paginated & filterable published catalog (`category`, `language`, `contentType`, `ageRating`, `search`, `page`, `size`, `sort`) | `CONTENT_VIEW` |
| `GET` | `/api/v1/content/{id}` | Full metadata for a specific published content item | `CONTENT_VIEW` |
| `GET` | `/api/v1/content/featured` | Top hero rail featured published items | `CONTENT_VIEW` |
| `GET` | `/api/v1/categories` | List active categories | `CATEGORY_VIEW` / `CONTENT_VIEW` |
| `GET` | `/api/v1/categories/{id}` | Get category details | `CATEGORY_VIEW` |
| `GET` | `/api/v1/languages` | List active languages | `LANGUAGE_VIEW` / `CONTENT_VIEW` |
| `GET` | `/api/v1/languages/{id}` | Get language details | `LANGUAGE_VIEW` |

### 4.2 Admin Management APIs
| Method | Endpoint | Description | Required Permission |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/admin/categories` | Create new category | `CATEGORY_CREATE` |
| `PUT` | `/api/v1/admin/categories/{id}` | Update category details | `CATEGORY_UPDATE` |
| `DELETE`| `/api/v1/admin/categories/{id}` | Deactivate category (`active = false`) | `CATEGORY_DELETE` |
| `POST` | `/api/v1/admin/languages` | Create new language | `LANGUAGE_CREATE` |
| `PUT` | `/api/v1/admin/languages/{id}` | Update language details | `LANGUAGE_UPDATE` |
| `DELETE`| `/api/v1/admin/languages/{id}` | Deactivate language (`active = false`) | `LANGUAGE_DELETE` |
| `PUT` | `/api/v1/admin/content/{id}/metadata` | Update content taxonomy, categories & languages | `CONTENT_METADATA_UPDATE` / `CONTENT_UPDATE` |
| `POST` | `/api/v1/admin/content/{id}/categories/{catId}` | Assign single category to content | `CONTENT_METADATA_UPDATE` / `CONTENT_UPDATE` |
| `DELETE`| `/api/v1/admin/content/{id}/categories/{catId}` | Remove category association from content | `CONTENT_METADATA_UPDATE` / `CONTENT_UPDATE` |
| `POST` | `/api/v1/admin/content/{id}/languages/{langId}` | Assign available language to content | `CONTENT_METADATA_UPDATE` / `CONTENT_UPDATE` |
| `DELETE`| `/api/v1/admin/content/{id}/languages/{langId}` | Remove language association from content | `CONTENT_METADATA_UPDATE` / `CONTENT_UPDATE` |

---

## 5. Verification & Test Suite

The test suite contains **171 automated test cases** across all modules:
1. `CategoryManagementTest` (7 tests): Category CRUD, duplicate name/slug rejection, deactivation, exclusion from consumer listing, RBAC rejection.
2. `LanguageManagementTest` (6 tests): Language CRUD, duplicate code rejection, deactivation, exclusion from consumer listing, RBAC rejection.
3. `ContentMetadataAndFilterTest` (12 tests): Multi-category & multi-language associations, metadata updates, category slug filtering, language code filtering, content type & age rating filtering, composite multi-filtering, keyword search, strict non-published exclusion, database pagination & max page size cap, whitelisted safe sorting vs invalid sort field rejection, and RBAC authorization.
4. Regression verification: All previous Phase 1 – 5.1 test suites pass with zero regressions.

---

## 6. Future Search Engine Architecture Roadmap

While PostgreSQL-compatible ILIKE / Full-Text querying satisfies Phase 5.2 catalog needs, future high-scale search needs (fuzzy spelling tolerance, phonetics for multilingual titles, vector embeddings for semantic search, and real-time faceted analytics) can be introduced in a dedicated Search Phase (e.g. OpenSearch / Elasticsearch cluster or pgvector) without breaking existing catalog client contracts.
