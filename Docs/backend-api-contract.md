# CommunityOTT Backend API Contract Specifications (iOS & Android Shared Backend)

This document outlines the API contract specifications required by the CommunityOTT client applications (iOS & Android). All unknown endpoints and response schemas are explicitly marked with `TBD — Backend Contract Required`.

---

## 1. Environment & Base URL

| Environment | Base URL Format | Status |
| :--- | :--- | :--- |
| **Development** | `https://api-dev.communityott.org/v1` | Placeholder Defined |
| **Staging** | `https://api-staging.communityott.org/v1` | TBD — Backend Contract Required |
| **Production** | `https://api.communityott.org/v1` | TBD — Backend Contract Required |

---

## 2. Authentication & Authorization

### Headers
All authenticated requests must contain:
```http
Authorization: Bearer <access_token>
Content-Type: application/json
```

### Authentication Endpoints (TBD — Backend Contract Required)

#### 2.1 Sign In / Member Login
- **Endpoint**: `POST /auth/login`
- **Request Body**:
  ```json
  {
    "email": "user@communityott.org",
    "password": "TBD — Backend Contract Required"
  }
  ```
- **Response (`200 OK`)**:
  ```json
  {
    "accessToken": "TBD — Token Format",
    "refreshToken": "TBD — Token Format",
    "user": {
      "id": "string",
      "name": "string",
      "email": "string",
      "preferredLanguage": "Telugu",
      "isSubscribed": true
    }
  }
  ```

#### 2.2 Mobile OTP Request
- **Endpoint**: `POST /auth/otp/request`
- **Request Body**: `TBD — Backend Contract Required`

#### 2.3 Token Refresh
- **Endpoint**: `POST /auth/token/refresh`
- **Request Body**: `TBD — Backend Contract Required`

---

## 3. Content API Endpoints

#### 3.1 Home Sections
- **Endpoint**: `GET /content/home`
- **Response**: List of rails (`hero`, `continueWatching`, `featured`, `voicesOfSuccess`, `folkAndCulture`).
- **Status**: `TBD — Backend Contract Required`

#### 3.2 Content Details
- **Endpoint**: `GET /content/{id}`
- **Response**: `ContentItem` object with metadata, stream references, and related content.

#### 3.3 Content by Category
- **Endpoint**: `GET /content/category/{categoryID}`
- **Query Parameters**: `page`, `limit`, `sort` (`TBD — Backend Contract Required`).

#### 3.4 Batch Content Lookups
- **Endpoint**: `POST /content/batch`
- **Request Body**: `{"ids": ["item-1", "item-2"]}`

---

## 4. Media & Video Streaming API

#### 4.1 Fetch Media Stream
- **Endpoint**: `GET /media/stream/{contentID}`
- **Headers**: `Authorization: Bearer <access_token>`
- **Response (`200 OK`)**:
  ```json
  {
    "id": "stream-101",
    "contentID": "item-101",
    "streamURL": "https://cdn.communityott.org/hls/master.m3u8",
    "format": "hls",
    "duration": 2520.0,
    "subtitleTracks": [
      {
        "id": "sub-te",
        "languageCode": "te",
        "languageName": "Telugu",
        "url": "https://cdn.communityott.org/subs/te.vtt"
      }
    ]
  }
  ```
- **Status**: `TBD — Backend Contract Required`

---

## 5. Playback Progress Sync

#### 5.1 Sync Progress
- **Endpoint**: `POST /playback/progress/{contentID}`
- **Request Body**:
  ```json
  {
    "currentTime": 450.5,
    "duration": 2520.0,
    "lastUpdated": 1786200000
  }
  ```

---

## 6. Saved / My List API

- `GET /user/saved`
- `POST /user/saved/{contentID}`
- `DELETE /user/saved/{contentID}`

---

## 7. Error Format Specification

All error responses from the API must follow the standard envelope:
```json
{
  "error": {
    "code": "UNAUTHORIZED",
    "message": "User session expired. Please sign in again.",
    "details": null
  }
}
```
