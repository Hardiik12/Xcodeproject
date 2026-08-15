# CommunityOTT Monolithic Backend — Phase 5.5 Walkthrough & Architecture Report

---

## 1. Executive Summary

**Phase 5.5 — Video Processing Job Architecture + FFmpeg/FFprobe Worker Foundation** introduces the asynchronous background video processing subsystem into the monolithic CommunityOTT Spring Boot backend.

The architecture connects video asset ingestion with background media probing, media validation, and lifecycle coordination while strictly maintaining a **single monolithic deployment** (no microservices, no Kafka, no external distributed queues, no uncontrolled thread spawns).

---

## 2. Architecture & Components

```
Admin / Content Manager (Client)
         │
         ▼
    VideoAsset (UPLOADED)
         │
         ▼ (Auto-enqueued on upload or via API)
    VideoProcessingJob (QUEUED)
         │
         ▼
Bounded ThreadPoolTaskExecutor (videoProcessingExecutor)
         │
         ├── Safe ProcessRunner (ProcessBuilder, stream drains, bounded buffers, timeouts)
         │
         ├── FFprobe (JSON extraction: duration, dimensions, bitrates, codecs)
         │      ↓
         │   Media Validation (Validates video stream, dimensions > 0, duration > 0)
         │
         └── Workspace Scratch Isolation (Temp scratch directory with guaranteed cleanup)
         │
         ▼
    VideoProcessingJob (COMPLETED / FAILED)
    VideoAsset (READY / FAILED, populated duration, width, height, bitrate)
    Content (READY / FAILED)
```

### Component Breakdown
1. **`VideoProcessingJob` Domain Model & Schema Migration**:
   - Flyway migration `V10__create_video_processing_jobs_schema.sql` creates `video_processing_jobs` table.
   - Enums: `ProcessingJobType` (`PROBE`, `TRANSCODE`, `PACKAGE_HLS`, `VALIDATE_OUTPUT`) and `ProcessingJobStatus` (`QUEUED`, `PROCESSING`, `COMPLETED`, `FAILED`, `CANCELLED`).
   - Concurrency control via `@Version Long version` and status transition methods (`canTransitionTo`).
2. **Safe `ProcessRunner`**:
   - `DefaultProcessRunner` uses `ProcessBuilder(List<String> commandArgs)` to execute commands with individual arguments (zero shell interpolation, preventing command injection).
   - Asynchronous stream drains for `stdout` and `stderr` capped at 1 MB memory limit to prevent JVM heap exhaustion.
   - Strict process timeout with `process.destroyForcibly()` on expiry or thread interruption.
3. **FFprobe Integration**:
   - `DefaultFFprobeService` executes `ffprobe -v quiet -print_format json -show_format -show_streams <path>` and parses machine-readable JSON into `MediaProbeResult`.
   - Validates that media contains a valid video stream, positive dimensions, and positive duration before marking valid.
4. **Asynchronous Bounded Worker**:
   - `VideoProcessingConfig` configures a bounded `ThreadPoolTaskExecutor` (`videoProcessingExecutor`) with bounded queue and core/max pool sizing matching `maxConcurrentJobs`.
   - `DefaultVideoProcessor` downloads the source video from MinIO into an isolated temporary workspace, runs FFprobe, updates `VideoAsset` and `Content` state, and guarantees recursive cleanup in a `finally` block.
5. **Idempotency & Stale Crash Recovery**:
   - `VideoProcessingService` prevents duplicate active jobs (`QUEUED` or `PROCESSING`) for the same video asset and type.
   - `recoverStaleJobs()` detects jobs stuck in `PROCESSING` past the configurable heartbeat timeout and requeues them if attempts remain.
6. **REST Controller**:
   - `POST /api/v1/admin/videos/{videoId}/processing` (Requires `VIDEO_PROCESS`)
   - `GET /api/v1/admin/videos/{videoId}/processing` (Requires `VIDEO_VIEW`, `CONTENT_VIEW`, or `VIDEO_PROCESS`)
   - `POST /api/v1/admin/videos/{videoId}/processing/retry` (Requires `VIDEO_RETRY`)

---

## 3. Database Schema (`V10__create_video_processing_jobs_schema.sql`)

```sql
CREATE TABLE IF NOT EXISTS video_processing_jobs (
    id BIGSERIAL PRIMARY KEY,
    video_asset_id BIGINT NOT NULL,
    job_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'QUEUED',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    priority INTEGER NOT NULL DEFAULT 0,
    worker_id VARCHAR(100),
    error_code VARCHAR(100),
    error_message VARCHAR(1000),
    media_metadata_json TEXT,
    created_by BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    failed_at TIMESTAMP WITH TIME ZONE,
    last_heartbeat_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_video_processing_jobs_asset FOREIGN KEY (video_asset_id) REFERENCES video_assets(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_video_processing_jobs_asset_id ON video_processing_jobs(video_asset_id);
CREATE INDEX IF NOT EXISTS idx_video_processing_jobs_status ON video_processing_jobs(status);
CREATE INDEX IF NOT EXISTS idx_video_processing_jobs_job_type ON video_processing_jobs(job_type);
CREATE INDEX IF NOT EXISTS idx_video_processing_jobs_stale ON video_processing_jobs(status, last_heartbeat_at);
```

---

## 4. Local Development & FFmpeg Installation

### Missing Binary Handling
If `ffmpeg` or `ffprobe` is not installed on the host machine:
- The backend uses configurable properties (`communityott.video.processing.ffmpeg-path`, `communityott.video.processing.ffprobe-path`).
- Automated tests mock `FFprobeService` and `ProcessRunner`, allowing the full test suite to execute and pass in CI/CD without local binaries.

### Installing FFmpeg on macOS:
```bash
brew install ffmpeg
```

---

## 5. Scope & Deferred Work

Phase 5.5 explicitly establishes the processing job state machine, FFprobe metadata extractor, and bounded worker foundation.

### Deferred to Future Phases:
- **Phase 5.6**: FFmpeg multi-bitrate video transcoding ladder (1080p, 720p, 480p, 360p, 144p).
- **Phase 5.7**: HLS segmenting (`.ts` / `.m4s` segments) and master/variant playlist (`master.m3u8`) packaging.
- **Phase 5.8**: CDN distribution, signed playback URLs, and edge caching.

---

## 6. Verification Results

```text
[INFO] Results:
[INFO] 
[INFO] Tests run: 218, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  01:08 min
[INFO] Finished at: 2026-08-16T00:47:15+05:30
[INFO] ------------------------------------------------------------------------
```
