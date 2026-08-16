# Phase 5.8 — Secure Media Delivery + CDN-Ready Architecture Walkthrough

## 1. Overview & Architecture
In **Phase 5.8**, we established the secure media delivery foundation and CDN-ready architecture for the CommunityOTT platform. This layer decouples the user-facing playback authorization from the underlying storage mechanism and cloud delivery network, allowing consumer OTT clients (iOS / Android / Web) to securely access adaptive bitrate HLS video content while keeping media objects private and protected against unauthorized enumeration, hotlinking, and rate abuse.

```
+-----------------------------------------------------------------------------------+
|                                 CLIENT (iOS / Android / Web)                      |
+-----------------------------------------------------------------------------------+
                                        |
                 1. GET /api/v1/content/{id}/playback (Bearer JWT)
                                        v
+-----------------------------------------------------------------------------------+
|                        SPRING BOOT MONOLITHIC BACKEND                             |
|                                                                                   |
|  +-----------------------+   +------------------------+   +--------------------+  |
|  | RbacAuthorization     |   | ContentAccessService   |   | PlaybackRateLimiter|  |
|  | (VIDEO_VIEW)          |-->| (PUBLISHED/READY)      |-->| (Redis Window)     |  |
|  +-----------------------+   +------------------------+   +--------------------+  |
|                                       |                                           |
|                                       v                                           |
|                     +-----------------------------------+                         |
|                     |       MediaDeliveryService        |                         |
|                     +-----------------------------------+                         |
|                                       |                                           |
|            [DeliveryMode.LOCAL]       |       [DeliveryMode.CDN]                  |
|                      +----------------+----------------+                          |
|                      v                                 v                          |
|         +-------------------------+      +-------------------------+              |
|         |MinioMediaDeliveryProvider|     | CdnMediaDeliveryProvider|              |
|         +-------------------------+      +-------------------------+              |
+----------------------|---------------------------------|--------------------------+
                       | (Presigned URL)                 | (CDN Base URL + Token)
                       v                                 v
          +-------------------------+      +-------------------------+
          |      MinIO Storage      |      |   CDN Edge Distribution |
          |     (Local Origin)      |      |     (Production Edge)   |
          +-------------------------+      +-------------------------+
```

---

## 2. Media Delivery Abstraction & Providers

### Delivery Mode Configuration
- Configured via `communityott.media.delivery`:
  - `mode`: `LOCAL` or `CDN` (switchable via `COMMUNITYOTT_MEDIA_DELIVERY_MODE`).
  - `playback-url-ttl-seconds`: Configurable TTL (default 900 seconds / 15 minutes).
  - `cdn.base-url`: CDN domain base URL (e.g. `https://cdn.communityott.com`).
  - `cdn.signing-key-id`: Key ID for CDN token/signature authentication.
  - `cdn.token-auth-enabled`: Toggle for tokenized query parameters.
  - `rate-limit.enabled`: Toggle for Redis rate limiting.
  - `rate-limit.max-requests-per-minute`: Maximum playback URL generation requests allowed per minute (default: 30).

### Provider Interface & Implementations
- **`MediaDeliveryProvider`** (`com.communityott.content.delivery`):
  - Common contract defining `getMode()`, `getProviderName()`, and `generateDeliveryInfo(VideoHlsPackage, Duration)`.
- **`MinioMediaDeliveryProvider`**:
  - Implements `DeliveryMode.LOCAL`.
  - Generates time-limited S3/MinIO presigned GET URLs for `master.m3u8` using `ObjectStorageService`.
- **`CdnMediaDeliveryProvider`**:
  - Implements `DeliveryMode.CDN`.
  - Generates CDN-edge delivery URLs matching the master playlist key with time-limited token/signature parameters.

---

## 3. Playback Authorization & Access Policy (`ContentAccessService`)

Before issuing playback access, the backend enforces a 7-stage authorization and verification pipeline:
1. **User Authentication & RBAC**:
   - The user must be authenticated via Bearer JWT.
   - The user must possess the `VIDEO_VIEW` permission (standard for `USER`, `CONTENT_MANAGER`, and `SUPER_ADMIN`).
2. **Rate Limiting**:
   - Evaluated by `PlaybackRateLimiter` using Redis window keys. Exceeding 30 requests/minute triggers HTTP 429 (`PLAYBACK_RATE_LIMITED`).
3. **Content Resolution & State**:
   - Content must exist and be in `ContentStatus.PUBLISHED` state.
   - Any request for `DRAFT`, `UPLOADING`, `PROCESSING`, `FAILED`, `UNPUBLISHED`, or `ARCHIVED` content is rejected with HTTP 409 (`CONTENT_NOT_AVAILABLE`).
4. **Video Asset Resolution & State**:
   - Resolves the active `VideoAsset` for the content.
   - Must be in `VideoAssetStatus.READY`. Unready assets reject with HTTP 409 (`VIDEO_NOT_READY`).
5. **HLS Package Resolution & State**:
   - Resolves `VideoHlsPackage` for the video asset.
   - Must be in `HlsPackageStatus.READY` and contain a non-empty `masterPlaylistKey`. Missing or unready packages reject with HTTP 409 (`VIDEO_NOT_READY`).
6. **Rendition Metadata Gathering**:
   - Loads all packaged variants from `video_hls_variants` ordered by resolution height (`1080p`, `720p`, etc.).
7. **Delivery Response Generation**:
   - Dispatches to the active `MediaDeliveryProvider` to obtain a time-limited playback URL.

---

## 4. Endpoints & Response Schema

### `GET /api/v1/content/{id}/playback`
- **Security**: `@PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'VIDEO_VIEW')")`
- **Response Format**:
```json
{
  "success": true,
  "message": "Playback authorization granted successfully",
  "data": {
    "contentId": 101,
    "title": "Telugu Heritage Documentary",
    "videoAssetId": 202,
    "protocol": "HLS",
    "playbackUrl": "http://localhost:9000/communityott-media/hls/101/202/master.m3u8?X-Amz-Algorithm=...",
    "expiresAt": "2026-08-16T18:15:00Z",
    "durationSeconds": 7200,
    "deliveryMode": "LOCAL",
    "deliveryProvider": "MINIO_LOCAL",
    "availableRenditions": [
      {
        "resolution": "1080p",
        "width": 1920,
        "height": 1080,
        "bandwidthBps": 5970800,
        "averageBandwidthBps": 5192000,
        "codecs": "avc1.640028,mp4a.40.2",
        "frameRate": 24.0
      },
      {
        "resolution": "720p",
        "width": 1280,
        "height": 720,
        "bandwidthBps": 3120000,
        "averageBandwidthBps": 2750000,
        "codecs": "avc1.64001f,mp4a.40.2",
        "frameRate": 24.0
      }
    ]
  },
  "timestamp": "2026-08-16T18:00:00Z"
}
```

---

## 5. Security & Protection Controls

- **Private Storage**: MinIO object storage buckets remain private and unlisted. No credentials, access keys, secret keys, or internal directory paths are exposed in DTO responses or error payloads.
- **Path Safety**: Manifest keys use sanitized alphanumeric storage keys generated via `StorageKeyGenerator`. Path traversal attempts (`..`, `\`) and external URL injections are blocked.
- **Rate Limiting**: Playback authorization requests are rate-limited via Redis (`communityott:ratelimit:playback:{userId}:{minuteWindow}`) without rate-limiting downstream video segment streaming.

---

## 6. Test Suite & Verification Summary

- **Total Test Cases**: **256 passed** (0 failures, 0 errors, 0 skipped).
- **New Test Suite**: `MediaDeliverySecurityTest.java` (19 tests):
  1. `test01_publishedContentCanRequestPlayback_LocalMinio`: 200 OK with valid presigned URL and rendition metadata.
  2. `test02_unpublishedContentRejected`: 409 Conflict (`CONTENT_NOT_AVAILABLE`).
  3. `test03_draftContentRejected`: 409 Conflict (`CONTENT_NOT_AVAILABLE`).
  4. `test04_processingContentRejected`: 409 Conflict (`CONTENT_NOT_AVAILABLE`).
  5. `test05_failedContentRejected`: 409 Conflict (`CONTENT_NOT_AVAILABLE`).
  6. `test06_archivedContentRejected`: 409 Conflict (`CONTENT_NOT_AVAILABLE`).
  7. `test07_nonExistentContentReturns404`: 404 Not Found (`CONTENT_NOT_FOUND`).
  8. `test08_missingVideoAssetReturns409`: 409 Conflict (`VIDEO_NOT_READY`).
  9. `test09_videoAssetNotReadyReturns409`: 409 Conflict (`VIDEO_NOT_READY`).
  10. `test10_missingHlsPackageReturns409`: 409 Conflict (`VIDEO_NOT_READY`).
  11. `test11_hlsPackageNotReadyReturns409`: 409 Conflict (`VIDEO_NOT_READY`).
  12. `test12_unauthenticatedRequestRejected`: 401 Unauthorized.
  13. `test13_cdnDeliveryModeGeneratesCdnUrl`: Generates CDN URL (`https://cdn.communityott.com/...`).
  14. `test14_cdnTokenAuthEnabled`: Appends token signature parameters when enabled.
  15. `test15_rateLimitingEnforced`: Rejects requests exceeding 30 req/min with HTTP 429 (`PLAYBACK_RATE_LIMITED`).
  16. `test16_noCredentialLeakageInResponse`: Verifies no password or secret leakage.
  17. `test17_adminCanAlsoAccessPlayback`: Verifies Super Admin authorization.
  18. `test18_providerContractTest`: Verifies provider independence and contract consistency.
  19. `test19_realMinioLivePlaybackTest`: Verifies live upload and presigned GET signature against Docker MinIO.

---

## 7. Deferred Work (Strict Scope Boundaries)
- **Vendor-Specific CDN Integrations**: CloudFront/Cloudflare key pairs and custom edge workers are deferred to infrastructure provisioning phases.
- **DRM**: Apple FairPlay, Google Widevine, and Microsoft PlayReady encryption.
- **Playback Sessions**: Watch progress tracking, resume position, heartbeats, and playback analytics.
