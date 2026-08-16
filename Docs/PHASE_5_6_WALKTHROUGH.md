# Phase 5.6: Multi-Resolution FFmpeg Transcoding Pipeline — Verification Walkthrough

## Overview
Phase 5.6 implements the complete multi-resolution video transcoding pipeline for the CommunityOTT monolithic backend. The worker pipeline downloads raw source videos from MinIO object storage, analyzes source resolution and codecs with FFprobe, determines the optimal adaptive resolution ladder (1080p, 720p, 480p, 360p, 144p), executes FFmpeg encoding with strict aspect ratio scaling and web-optimized `+faststart` metadata, uploads all renditions to MinIO, and records metadata and checksums in PostgreSQL.

---

## Key Components Implemented

### 1. Database Schema & Domain Entity
- **Flyway Migration (`V11__create_video_renditions_schema.sql`)**:
  - `video_renditions` table with foreign key to `video_assets(id)`, unique constraint `(video_asset_id, resolution)`, and status indexing.
- **Domain Models**:
  - `VideoResolution`: Standard OTT presets (`1080p`, `720p`, `480p`, `360p`, `144p`) with non-upscaling filter `getLadderForSource(sourceHeight)`.
  - `RenditionStatus`: `PROCESSING`, `READY`, `FAILED`.
  - `VideoRendition`: JPA Entity tracking file sizes, bitrates, dimensions, codecs, MinIO storage keys, and SHA-256 checksums.
  - `VideoRenditionRepository`: Data access with indexed lookups by asset ID and resolution.

### 2. Transcoding Engine & Worker Architecture
- **`TranscodeProfile` & `FFmpegTranscodeService`**:
  - Profile definitions (width, height, target video/audio bitrate, preset).
  - Safe array-based FFmpeg CLI generation:
    `ffmpeg -y -i <source> -vf scale=-2:<height> -c:v libx264 -pix_fmt yuv420p -preset <preset> -b:v <bitrate>k -maxrate <maxrate>k -bufsize <bufsize>k -c:a aac -b:a <abitrate>k -ar 48000 -ac 2 -movflags +faststart <target.mp4>`
  - Timeout and stream-buffering execution via `ProcessRunner`.
- **`DefaultVideoProcessor`**:
  - Probes source media using `FFprobeService`.
  - Calculates non-upscaled rendition ladder.
  - Sequentially encodes renditions into isolated `/tmp/communityott-videos/job_<id>_<uuid>` scratch directories.
  - Computes SHA-256 for each output rendition.
  - Streams renditions to MinIO (`renditions/asset_<id>/<resolution>.mp4`).
  - Persists `VideoRendition` records and transitions `VideoAsset` and `Content` to `READY`.
  - Guaranteed `finally` cleanup of local temporary directories.

### 3. REST APIs & Security
- `POST /api/v1/admin/videos/{videoId}/transcode`: Manually trigger or re-enqueue transcoding jobs (requires `VIDEO_PROCESS`).
- `GET /api/v1/admin/videos/{videoId}/renditions`: Query all generated renditions for an asset (requires `VIDEO_PROCESS`).

---

## Test Verification

Full test suite executed with **224 tests passing (0 failures, 0 errors, 0 skipped)**:

```text
[INFO] Results:
[INFO] 
[INFO] Tests run: 224, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

### New Tests in `VideoTranscodingPipelineTest`:
1. `test01_resolutionLadderSelection`: Validates resolution ladder prevents upscaling (e.g. 1080p yields 5 renditions; 720p yields 4 renditions; low resolution yields fallback 144p).
2. `test02_ffmpegCommandGeneration`: Validates safe FFmpeg CLI arguments, scale filter `-vf scale=-2:720`, audio codec `aac`, and `+faststart`.
3. `test03_e2eTranscodingPipeline`: Simulates full pipeline (probe -> multi-resolution transcode -> SHA-256 calculation -> MinIO upload -> VideoRendition entity persistence -> Content status `READY`).
4. `test04_enqueueTranscodingEndpoint`: Validates `POST /api/v1/admin/videos/{videoId}/transcode` accepts and queues `TRANSCODE` jobs.
5. `test05_listRenditionsEndpoint`: Validates `GET /api/v1/admin/videos/{videoId}/renditions` returns rendition list.
6. `test06_securityAuthorizationCheck`: Confirms RBAC authorization blocks non-administrative requests with `403 Forbidden`.
