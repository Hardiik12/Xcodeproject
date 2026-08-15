# CommunityOTT Backend — Phase 5.4 Walkthrough
## Video Upload + MinIO Object Storage & VideoAsset Foundation

---

## 1. Executive Summary

Phase 5.4 implements the video upload and object storage ingestion layer for the CommunityOTT backend using **MinIO S3-compatible Object Storage**.

CommunityOTT is an OTT streaming platform where only authorized administrative/content-management roles (`SUPER_ADMIN`, `CONTENT_MANAGER`) can upload source video assets.

This phase establishes:
- **`VideoAsset` Domain Model**: Database entity and Flyway migration `V9` capturing file metadata, dimensions, streaming bitrate, storage bucket, storage key, and SHA-256 checksums.
- **`ObjectStorageService` Abstraction**: Clean provider-agnostic storage abstraction with a [`MinioObjectStorageService`](file:///Users/hardik/Documents/IOS/CommunityOTT/backend/src/main/java/com/communityott/content/storage/MinioObjectStorageService.java) implementation using the official MinIO Java SDK.
- **Upload Validation**: Enforces MIME types (`video/mp4`, `video/quicktime`, `video/x-matroska`, `video/webm`), file extensions (`.mp4`, `.mov`, `.mkv`, `.webm`), and max file size constraints.
- **Streaming SHA-256 Checksum Calculation**: Calculates cryptographic digests during upload streaming without buffering entire gigabyte video payloads into memory.
- **Deterministic Canonical Object Keys**: Format `sources/{contentId}/{sha256Prefix}_{sanitizedFilename}` avoiding collision or path traversal vulnerabilities.
- **Lifecycle Progression**: Ingesting a video transitions the associated Content item to `UPLOADING` status, staging it for the upcoming async video transcoding pipeline (Phase 5.5).
- **Security & Authorization**: Protected by Spring Security and RBAC permission `@PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'VIDEO_UPLOAD')")`.

All **200 automated tests** across all phases pass with 100% success (`BUILD SUCCESS`).

---

## 2. Architecture & Ingestion Pipeline

```
Admin / Content Manager
        │
        │ POST /api/v1/admin/content/{id}/videos/upload (Multipart file)
        ▼
VideoManagementController
        │
        │ Checks @PreAuthorize("VIDEO_UPLOAD")
        ▼
VideoUploadService
        │
        ├── 1. Verify Content exists & is not ARCHIVED
        ├── 2. Validate MIME type, extension & size (VideoUploadValidator)
        ├── 3. Compute streaming SHA-256 (ChecksumUtility)
        ├── 4. Generate canonical storage key (StorageKeyGenerator)
        │
        ├── 5. Stream upload to MinIO Bucket (ObjectStorageService)
        │           │
        │           ▼
        │      MinIO S3 Storage ("communityott-media" / sources/{id}/...)
        │
        ├── 6. Save VideoAsset entity in PostgreSQL (video_assets table)
        └── 7. Transition Content status from DRAFT -> UPLOADING
```

---

## 3. Database Schema Migration (`V9__create_video_assets_schema.sql`)

```sql
-- ===================================================================
-- Phase 5.4: Video Assets and Object Storage Schema
-- ===================================================================

CREATE TABLE IF NOT EXISTS video_assets (
    id BIGSERIAL PRIMARY KEY,
    content_id BIGINT NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    checksum_sha256 VARCHAR(64) NOT NULL,
    storage_bucket VARCHAR(100) NOT NULL,
    storage_key VARCHAR(500) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL DEFAULT 'UPLOADED',
    duration_seconds INTEGER,
    width INTEGER,
    height INTEGER,
    bitrate_kbps INTEGER,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_video_assets_content FOREIGN KEY (content_id) REFERENCES content(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_video_assets_content_id ON video_assets(content_id);
CREATE INDEX IF NOT EXISTS idx_video_assets_status ON video_assets(status);
CREATE INDEX IF NOT EXISTS idx_video_assets_checksum ON video_assets(checksum_sha256);
```

---

## 4. REST API Specification

| Method | Endpoint | Description | Required Permission |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/admin/content/{contentId}/videos/upload` | Upload source video file (multipart form data) | `VIDEO_UPLOAD` |
| `GET` | `/api/v1/admin/content/{contentId}/videos` | List all video assets for a content item | `VIDEO_VIEW` or `CONTENT_VIEW` |
| `GET` | `/api/v1/admin/content/{contentId}/videos/{videoId}` | Get specific video asset details | `VIDEO_VIEW` or `CONTENT_VIEW` |
| `DELETE` | `/api/v1/admin/content/{contentId}/videos/{videoId}` | Soft delete asset and remove storage object | `VIDEO_DELETE` |

---

## 5. Security & RBAC Enforcement

| Role | Browse Videos | Upload Source Video | Delete Video Asset |
| :--- | :---: | :---: | :---: |
| **USER** | ❌ (Internal Assets) | ❌ (403 Forbidden) | ❌ (403 Forbidden) |
| **MANAGER** | ❌ | ❌ (403 Forbidden) | ❌ (403 Forbidden) |
| **CONTENT_MANAGER** | ✅ | ✅ | ❌ (403 Forbidden) |
| **SUPER_ADMIN** | ✅ | ✅ | ✅ |

---

## 6. Test Suite & Verification Results

### Test Execution Summary
- **Total Tests**: **200 tests run, 0 failures, 0 errors, 0 skipped** (`BUILD SUCCESS`).
- **Phase 5.4 Test Suite ([`VideoUploadAndStorageTest.java`](file:///Users/hardik/Documents/IOS/CommunityOTT/backend/src/test/java/com/communityott/VideoUploadAndStorageTest.java))**:
  1. `test01_successfulVideoUpload`: Uploads valid MP4 (`video/mp4`), verifies 201 Created, metadata persisted, SHA-256 matches, content status transitions from `DRAFT` to `UPLOADING`.
  2. `test02_contentManagerCanUploadVideo`: Confirms `CONTENT_MANAGER` with `VIDEO_UPLOAD` permission successfully uploads video.
  3. `test03_userCannotUploadVideo`: Confirms `USER` role is rejected with 403 Forbidden.
  4. `test04_managerCannotUploadVideoWithoutPermission`: Confirms `MANAGER` role lacking `VIDEO_UPLOAD` is rejected with 403 Forbidden.
  5. `test05_emptyVideoFileRejected`: Confirms empty/0-byte payload returns 400 `INVALID_VIDEO_FORMAT`.
  6. `test06_invalidMimeTypeRejected`: Confirms non-video MIME type (`image/png`) returns 400 `INVALID_VIDEO_FORMAT`.
  7. `test07_invalidExtensionRejected`: Confirms non-video extension (`.exe`) returns 400 `INVALID_VIDEO_FORMAT`.
  8. `test08_uploadToNonExistentContentReturns404`: Confirms upload to unknown contentId returns 404 `CONTENT_NOT_FOUND`.
  9. `test09_uploadToArchivedContentRejected`: Confirms upload to `ARCHIVED` content returns 400 `INVALID_CONTENT_STATE_TRANSITION`.
  10. `test10_listVideoAssetsForContent`: Confirms listing returns all assets associated with content item.
  11. `test11_getVideoAssetById`: Confirms retrieval returns specific video asset metadata.
  12. `test12_deleteVideoAsset`: Confirms deletion updates status to `DELETED` and invokes storage deletion.
  13. `test13_unauthorizedUserCannotDeleteVideoAsset`: Confirms unauthorized user cannot delete video assets (403 Forbidden).
  14. `test14_checksumIntegrityVerification`: Confirms deterministic SHA-256 digest computation across streams.

---

## 7. Next Phase Readiness: Phase 5.5 Transcoding Pipeline

With source video assets staged in MinIO and cataloged in PostgreSQL, the backend is ready for:
- **Phase 5.5**: Asynchronous Video Transcoding & FFmpeg Worker (HLS manifest generation, multi-bitrate renditions 1080p/720p/480p/360p).
- **Phase 5.6**: Renditions Cataloging & Subtitle Streams.
- **Phase 5.7**: Secure Video Playback API & Signed Streaming Token Verification for iOS/Android AVKit streaming.
