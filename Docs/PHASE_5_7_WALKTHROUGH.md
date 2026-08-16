# Phase 5.7 — HLS Packaging + Adaptive Streaming Manifest Generation Walkthrough

## Overview
In **Phase 5.7**, we designed, implemented, and verified the **HTTP Live Streaming (HLS)** packaging and adaptive streaming manifest generation pipeline for the CommunityOTT backend. The implementation takes transcoded multi-resolution MP4 renditions (from Phase 5.6) and packages them into **Fragmented MP4 (fMP4/CMAF)** segments using FFmpeg stream copy (`-c copy`), generates standard-compliant HLS variant and master playlists (`master.m3u8`), enforces manifest security (preventing path traversal and absolute URLs), uploads all artifacts to MinIO object storage, and persists the full metadata hierarchy in PostgreSQL (`video_hls_packages` and `video_hls_variants`).

---

## Key Components & Architecture

### 1. Database Schema & Migration (`V12__create_video_hls_schema.sql`)
- **`video_hls_packages`**:
  - `id`, `video_asset_id` (UNIQUE, FK -> `video_assets`), `master_playlist_key`, `storage_bucket`, `status`, `variant_count`, `target_duration_seconds`, `processing_job_id`, `error_code`, `error_message`, `completed_at`, `created_at`, `updated_at`, `version`.
- **`video_hls_variants`**:
  - `id`, `hls_package_id` (FK -> `video_hls_packages`), `rendition_id` (FK -> `video_renditions`), `resolution`, `width`, `height`, `bandwidth_bps`, `average_bandwidth_bps`, `codecs`, `frame_rate`, `playlist_key`, `init_segment_key`, `segment_count`, `target_duration_seconds`, `status`, `created_at`, `updated_at`.
  - Unique constraint on `(hls_package_id, resolution)` ensuring consistent multi-variant mapping.

### 2. Stream-Copy fMP4 HLS Packaging Engine (`DefaultFFmpegHlsPackagingService.java`)
- Executes FFmpeg using stream copy (`-c copy`) without re-encoding video or audio:
  ```bash
  ffmpeg -y -hide_banner -loglevel error -i rendition_1080p.mp4 \
    -c copy -f hls -hls_time 2 -hls_playlist_type vod \
    -hls_segment_type fmp4 -hls_fmp4_init_filename init.mp4 \
    -hls_segment_filename segment_%05d.m4s index.m3u8
  ```
- Generates:
  - `init.mp4` (fMP4 initialization segment containing movie header / moov box)
  - `segment_00000.m4s`, `segment_00001.m4s`, ... (~2-second media fragments)
  - `index.m3u8` (Variant media playlist referencing `init.mp4` via `#EXT-X-MAP:URI="init.mp4"`)

### 3. Master Playlist Generation & Security (`HlsManifestGenerator.java`)
- Builds RFC 8216-compliant master playlists:
  - `#EXTM3U`
  - `#EXT-X-VERSION:7`
  - `#EXT-X-INDEPENDENT-SEGMENTS`
  - `#EXT-X-STREAM-INF:BANDWIDTH=...,AVERAGE-BANDWIDTH=...,RESOLUTION=...x...,FRAME-RATE=...,CODECS="..."`
  - Variant URI relative path (`1080p/index.m3u8`, `720p/index.m3u8`, etc.)
- **Security Validation**:
  - Rejects any playlist entry containing path traversal characters (`..`, `\`) or absolute URL schemes (`http://`, `https://`, `ftp://`).

### 4. Manifest & Segment Validation (`HlsPackageValidator.java`)
- Validates the presence of `index.m3u8`, `init.mp4`, and all referenced `.m4s` segments.
- Asserts non-zero file sizes.
- Verifies required tags: `#EXTM3U`, `#EXT-X-TARGETDURATION`, `#EXT-X-ENDLIST`, and `#EXT-X-MAP:URI="init.mp4"`.

### 5. Storage Key Conventions (`StorageKeyGenerator.java`)
Deterministic layout in MinIO / S3:
```
hls/{contentId}/{videoAssetId}/master.m3u8
hls/{contentId}/{videoAssetId}/1080p/index.m3u8
hls/{contentId}/{videoAssetId}/1080p/init.mp4
hls/{contentId}/{videoAssetId}/1080p/segment_00000.m4s
hls/{contentId}/{videoAssetId}/720p/index.m3u8
...
```

### 6. Pipeline Orchestration & Endpoints
- Integrated within `DefaultVideoProcessor.java`:
  - Automatically enqueued and executed following `TRANSCODE` jobs or triggered as standalone `PACKAGE_HLS` jobs.
  - Uploads all generated files atomically to MinIO with correct MIME types (`application/vnd.apple.mpegurl`, `video/mp4`, `video/iso.segment`).
  - Sets `video_assets.status = READY` upon successful package completion.
- **REST Endpoints**:
  - `POST /api/v1/admin/videos/{videoId}/hls/package`: Enqueues an HLS packaging job (RBAC: `VIDEO_PROCESS`).
  - `GET /api/v1/admin/videos/{videoId}/hls`: Fetches the HLS package details and variant manifests (RBAC: `VIDEO_PROCESS`).

---

## Test Suite Verification

- **Total Tests**: 237 passed (0 failures, 0 errors, 0 skipped).
- **Test Suites Included**:
  - `VideoHlsPackagingTest` (13 tests): Stream copy command execution, fMP4 packaging, playlist parsing, path traversal protection, MinIO uploads, entity persistence, REST API RBAC checks, and end-to-end processing.
  - `VideoTranscodingPipelineTest` (14 tests): Multi-resolution transcoding and rendition generation.
  - `VideoProcessingArchitectureTest` (18 tests): Video processing jobs, worker lifecycle, probe parsing.
  - All existing Auth, OTP, RBAC, and Content Management test suites.
