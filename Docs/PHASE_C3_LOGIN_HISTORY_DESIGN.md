# Phase C.3 — Login History & Account Security Architecture Design

**Status**: `DESIGN APPROVED FOR IMPLEMENTATION`  
**Date**: August 21, 2026  
**Source of Truth**: `Docs/PHASE_C3_LOGIN_HISTORY_DESIGN.md`  
**Prerequisites**:
- Phase A: Authentication & Session Hardening (`442 tests passing`)
- Phase B.1 & B.2: Device Management + 2-Active-Device Limit (`10/10 tests passing`)
- Phase C.1 & C.2: Security Event Audit Infrastructure (`463 tests passing`)

---

## Executive Summary & Architectural Vision

Phase C.3 defines the user-facing **Login History & Account Security System** for CommunityOTT. While Phase C.2 built an internal, immutable, high-granularity `security_audit_events` infrastructure for administrators and security auditing, Phase C.3 creates a privacy-filtered, user-friendly **Login History Projection & Query Layer** tailored specifically for stream consumers on iOS and Web.

The core principle of Phase C.3 is **Privacy-First Account Security**: stream consumers receive transparent visibility into login activity, new device registrations, and blocked security events without exposing internal secrets, infrastructure topology, raw IP addresses, or complex security logs.

---

## 1. Architectural Distinction: C.2 Audit vs. C.3 Login History

| Dimension | Phase C.2 Internal Audit (`security_audit_events`) | Phase C.3 User-Facing History (`user_login_history`) |
| :--- | :--- | :--- |
| **Audience** | Security Operations, System Admins, Automated SIEM | Stream Consumers, End-User Account Security UI |
| **Granularity** | Microscopic (All events, trace IDs, MDC correlation) | Aggregated & Deduplicated (User-action level) |
| **Field Sensitivity**| Full raw IP, full User-Agent, request/trace IDs | Masked IP (`192.168.x.x`), normalized device details |
| **Immutability** | Strictly immutable, 1-year compliance retention | User-controlled, 90-day rolling retention |
| **User Deletion** | Anonymized (`user_id = NULL`), record retained | Hard-deleted (`ON DELETE CASCADE`) on account wipe |
| **User UX Goal** | Incident investigation & threat detection | Peace of mind, device control, suspicious activity alerts |

---

## 2. End-to-End Architecture Diagram

```
+-----------------------------------------------------------------------------------+
|                           PHASE C.2 SECURITY AUDIT LAYER                          |
|  security_audit_events Table (Internal, Raw IPs, User-Agents, Trace IDs, SIEM)  |
+-----------------------------------------------------------------------------------+
                                         |
                                         | Spring @EventListener (Async) / Event Bus
                                         v
+-----------------------------------------------------------------------------------+
|                    PHASE C.3 PROJECTION & DEDUPLICATION ENGINE                    |
|  - Deduplication: Merges (Login + Session + Device Reg) into 1 User Action        |
|  - Privacy Filter: Masks IP to subnet, parses User-Agent to OS/App Version       |
|  - UX Sanitizer: Translates raw error codes into friendly security messages      |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
|                        user_login_history PROJECTION TABLE                        |
|  (User-scoped, 90-day retention, optimized composite indexes for fast pagination)  |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
|                    GET /api/v1/account/login-history (REST API)                   |
|  (Scoped strictly to principal.getUserId(), 0 IDOR risk, pageable, rate-limited) |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
|                          iOS / WEB ACCOUNT SECURITY UI                            |
|  (Account -> Security & Login Activity -> Interactive Cards & Revoke Actions)    |
+-----------------------------------------------------------------------------------+
```

---

## 3. Login History Event Taxonomy & Classification

All 19 Phase C.2 event types are classified into three distinct exposure tiers:

### Tier 1: `USER_VISIBLE` (Displayed in User History)
1. **`AUTHN_LOGIN_SUCCESS`**: User successfully authenticated.
2. **`AUTHN_LOGIN_FAILED`**: Authentication attempt failed (wrong OTP code).
3. **`DEVICE_REGISTERED`**: A new device was added to the account.
4. **`DEVICE_REACTIVATED`**: A previously revoked device signed in again.
5. **`DEVICE_REVOKED`**: A device was manually revoked or signed out.
6. **`DEVICE_REPLACED`**: A device was swapped due to the 2-device limit.
7. **`DEVICE_LIMIT_REACHED`**: Registration blocked because 2 active devices exist.
8. **`SESSION_LOGOUT`**: User signed out of a specific session.
9. **`SESSION_LOGOUT_ALL`**: User executed "Sign Out All Devices".
10. **`SECURITY_TOKEN_REUSE`**: Suspicious refresh token reuse blocked.

### Tier 2: `SECURITY_ONLY` (Aggregated or Internal Metrics)
11. **`AUTHN_OTP_FAILED`**: Aggregated into `AUTHN_LOGIN_FAILED` to prevent enumeration.
12. **`AUTHN_OTP_EXPIRED`**: Internal OTP timeout; hidden to reduce UI clutter.
13. **`AUTHZ_DENIED`**: 403 Forbidden access attempt; internal RBAC metric.
14. **`SECURITY_IDOR_ATTEMPT`**: Unauthorized resource access; internal threat detection.
15. **`SECURITY_INVALID_TOKEN`**: Malformed/tampered JWT attempt; internal security metric.

### Tier 3: `NEVER_USER_VISIBLE` (Suppressed Low-Level Operational Events)
16. **`AUTHN_OTP_REQUESTED`**: Transient step before OTP entry.
17. **`SESSION_CREATED`**: Technical session creation; deduplicated into `AUTHN_LOGIN_SUCCESS`.
18. **`SESSION_REFRESH_SUCCESS`**: Silent background JWT rotation (every 15 mins).
19. **`SESSION_EXPIRED` / `SESSION_REFRESH_FAILED`**: Background session expiry.

---

## 4. User Experience (UX) Specifications

### Screen Hierarchy (iOS & Web)
```
Account
 └── Security & Login
      ├── Active Devices (Phase B.2 - Max 2 Active)
      └── Recent Login Activity (Phase C.3 - 90-Day Timeline)
```

### Visual Layout & Card Components
Each item in the Recent Login Activity feed renders:
- **Device Title**: e.g., `"iPhone 15 Pro"` or `"Chrome Desktop"`
- **Platform & OS Badge**: e.g., `iOS 18.6` • `CommunityOTT v1.4.0`
- **Timestamp**: Relative & Localized (e.g., `"Today • 10:32 PM"` or `"Aug 19, 2026 • 2:15 PM"`)
- **Status Indicator**:
  - `SUCCESS` (Green pill / icon)
  - `FAILED` (Muted orange pill)
  - `BLOCKED` (Dark red badge)
- **Current Device Pill**: Displayed **ONLY** if the entry corresponds to the client device issuing the API request (`CURRENT DEVICE`).
- **Safe Security Context**: `"Hyderabad, India (192.168.x.x)"`

---

## 5. Privacy Matrix & Data Classification

| Field | Tier | User-Facing Action | Rationale |
| :--- | :--- | :--- | :--- |
| `id` | `SAFE` | Exposed | Synthetic ID for pagination |
| `event_type` | `SAFE` | Exposed | User-friendly activity label |
| `status` | `SAFE` | Exposed | Activity status badge |
| `device_name` | `SAFE` | Exposed | Friendly device name |
| `platform` | `SAFE` | Exposed | Operating system / client type |
| `os_version` | `SAFE` | Exposed | System version for user recognition |
| `app_version` | `SAFE` | Exposed | Client app version |
| `occurred_at` | `SAFE` | Exposed | Formatted activity timestamp |
| `is_current_device` | `SAFE` | Computed | Indicates active session match |
| `masked_ip` | `SENSITIVE` | Masked | Subnet masked (e.g. `192.168.x.x`) |
| `approx_location` | `SENSITIVE` | Formatted | City/Country approximation |
| `ip_address` | `FORBIDDEN` | Redacted | Raw IP hidden to prevent tracking |
| `user_id` | `FORBIDDEN` | Omitted | Implicitly bound to token |
| `device_identifier` | `FORBIDDEN` | Omitted | Raw UUID/IDFV hidden |
| `session_id` | `FORBIDDEN` | Omitted | Raw DB session ID hidden |
| `refresh_token_hash`| `FORBIDDEN` | Omitted | Cryptographic hash hidden |
| `user_agent` | `FORBIDDEN` | Omitted | Raw browser string hidden |
| `request_id` / `trace_id` | `FORBIDDEN` | Omitted | Internal tracing IDs hidden |

---

## 6. IP Address & User-Agent Policies

### IP Address Policy: Option B (Masked IP)
- **IPv4 Masking**: `192.168.1.123` → `192.168.x.x`
- **IPv6 Masking**: `2001:0db8:85a3:0000:0000:8a2e:0370:7334` → `2001:db8::xxxx`
- **Location Field**: Initial implementation stores structured approximation (e.g., `"Hyderabad, India"` derived from headers or default gateway). No real-time external geolocation service integration is introduced in C.3.

### User-Agent Policy: Structured Parsing
Raw User-Agent strings (e.g., `Mozilla/5.0 (iPhone; CPU iPhone OS 18_6 like Mac OS X)...`) are parsed upon event receipt and converted into structured attributes:
- **Platform**: `IOS`, `ANDROID`, `WEB`, `TVOS`
- **Device Model**: `iPhone 15 Pro`, `MacBook Air`, `Chrome Desktop`
- **OS Version**: `iOS 18.6`, `macOS 15.0`
- **App Version**: `1.4.0`

---

## 7. Current Device Determination Algorithm

`is_current_device` MUST be calculated dynamically per request rather than stored statically:

```java
boolean isCurrentDevice = false;
if (authenticatedSession != null && authenticatedSession.getDeviceEntity() != null) {
    Long currentDeviceId = authenticatedSession.getDeviceEntity().getId();
    isCurrentDevice = currentDeviceId.equals(historyItem.getDeviceId()) 
                     && authenticatedSession.getDeviceEntity().isActive();
}
```

- **Validation Rule**: Bound strictly to the authenticated `sid` (session ID) contained in the request's JWT access token.
- **Revocation Safety**: If a device is revoked or signed out, `is_current_device` automatically evaluates to `false`.

---

## 8. Active Sessions vs. Login History

```
+-----------------------------------------------------------------------------------+
|                         ACTIVE DEVICES / SESSIONS (Phase B.2)                     |
|  - Goal: Live Authorization Management ("Who has access right now?")             |
|  - Limit: Maximum 2 active registered devices                                     |
|  - UI Actions: "Revoke Device", "Sign Out Session"                                |
|  - Lifecycle: Mutable (Active -> Revoked)                                         |
+-----------------------------------------------------------------------------------+
                                         │
                                         │ Complementary System
                                         ▼
+-----------------------------------------------------------------------------------+
|                          LOGIN HISTORY FEED (Phase C.3)                           |
|  - Goal: Historical Transparency ("What security events occurred?")               |
|  - Retention: 90-day rolling timeline                                             |
|  - UI Actions: Read-only timeline view with links to Revoke active sessions       |
|  - Lifecycle: Immutable append-only projection                                    |
+-----------------------------------------------------------------------------------+
```

---

## 9. Choice of Data Model: Dedicated Projection Table (Option A)

We evaluate three potential data models for Phase C.3:

- **Option A: Dedicated `user_login_history` Projection Table** (CHOSEN)
  - Asynchronously populated from C.2 events via Spring `@EventListener`.
  - *Pros*: Maximum query performance, clean index isolation, database-level pagination, independent retention (90 days vs 1 year SIEM), clean GDPR user-deletion compliance (`ON DELETE CASCADE`).
  - *Cons*: Slight storage duplication (sanitized subset).
- **Option B: Dynamic Query/Projection over `security_audit_events`**
  - *Pros*: Zero storage duplication.
  - *Cons*: Slow JSONB metadata scanning, query pollution across internal security logs, risk of raw secret/IP leakage if projection fails.
- **Option C: Materialized View**
  - *Pros*: Relational abstraction.
  - *Cons*: High refresh latency, difficult to purge user data cleanly on demand.

**Decision**: **Option A** is approved.

---

## 10. Database Schema Plan (`V22__create_user_login_history.sql`)

```sql
-- Flyway Migration: V22__create_user_login_history.sql
-- User-Facing Login History & Account Security Projection

CREATE TABLE user_login_history (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id                 BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_type              VARCHAR(64) NOT NULL,
    status                  VARCHAR(32) NOT NULL,
    device_id               BIGINT REFERENCES devices(id) ON DELETE SET NULL,
    device_name             VARCHAR(128) NOT NULL,
    platform                VARCHAR(32) NOT NULL,
    os_version              VARCHAR(64),
    app_version             VARCHAR(64),
    masked_ip               VARCHAR(64) NOT NULL,
    approx_location         VARCHAR(128),
    user_message            VARCHAR(255) NOT NULL,
    occurred_at             TIMESTAMPTZ NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    CONSTRAINT chk_login_history_status CHECK (status IN ('SUCCESS', 'FAILED', 'BLOCKED'))
);

-- Optimized Composite Indexes for User Queries
CREATE INDEX idx_login_history_user_occurred ON user_login_history (user_id, occurred_at DESC);
CREATE INDEX idx_login_history_user_type_occurred ON user_login_history (user_id, event_type, occurred_at DESC);
```

---

## 11. Event Deduplication & Aggregation Strategy

When a user logs in on a new device, Phase C.2 generates three simultaneous internal events:
`AUTHN_LOGIN_SUCCESS` + `SESSION_CREATED` + `DEVICE_REGISTERED`.

The C.3 Projection Engine deduplicates these events within a 5-second window:
1. If `DEVICE_REGISTERED` is present → Record single item: `NEW_DEVICE_LOGIN` (`"Signed in on new device"`).
2. If `DEVICE_REACTIVATED` is present → Record single item: `DEVICE_REACTIVATED` (`"Reactivated device sign-in"`).
3. Else → Record single item: `LOGIN_SUCCESS` (`"Signed in successfully"`).
4. `SESSION_CREATED` events are suppressed from the feed.

---

## 12. Retention & Purge Policy

- **Retention Window**: **90 Days**.
- **Purge Strategy**: Automated nightly background job (`@Scheduled(cron = "0 0 3 * * ?")`) executing:
  ```sql
  DELETE FROM user_login_history WHERE occurred_at < NOW() - INTERVAL '90 days';
  ```
- **Audit Separation**: Deleting rows from `user_login_history` does **NOT** touch `security_audit_events` (retained for 1 year for security compliance).

---

## 13. API Response Contract

### Endpoint: `GET /api/v1/account/login-history`

#### Query Parameters:
- `page` (integer, default `0`)
- `size` (integer, default `20`, max `50`)
- `from` (ISO-8601 timestamp, optional)
- `to` (ISO-8601 timestamp, optional)
- `eventType` (string, optional)
- `platform` (string, optional)

#### Response DTO (`LoginHistoryResponse`):
```json
{
  "items": [
    {
      "id": 1042,
      "event": "NEW_DEVICE_LOGIN",
      "status": "SUCCESS",
      "device_name": "iPhone 15 Pro",
      "platform": "IOS",
      "os_version": "18.6",
      "app_version": "1.4.0",
      "masked_ip": "192.168.x.x",
      "approx_location": "Hyderabad, India",
      "is_current_device": true,
      "user_message": "Signed in on new device",
      "occurred_at": "2026-08-21T22:30:00Z"
    },
    {
      "id": 1039,
      "event": "LOGIN_FAILED",
      "status": "FAILED",
      "device_name": "Android Phone",
      "platform": "ANDROID",
      "os_version": "14.0",
      "app_version": "1.4.0",
      "masked_ip": "10.0.x.x",
      "approx_location": "Vijayawada, India",
      "is_current_device": false,
      "user_message": "Sign-in attempt failed (invalid code)",
      "occurred_at": "2026-08-20T18:15:22Z"
    }
  ],
  "page": 0,
  "size": 20,
  "total_items": 42,
  "total_pages": 3,
  "has_next": true
}
```

---

## 14. Security Event Mapping Table

| Internal Security Event | User-Facing Display Event | Friendly User Message |
| :--- | :--- | :--- |
| `AUTHN_LOGIN_SUCCESS` | `LOGIN_SUCCESS` | `"Signed in successfully"` |
| `DEVICE_REGISTERED` | `NEW_DEVICE_LOGIN` | `"Signed in on new device"` |
| `DEVICE_REACTIVATED` | `DEVICE_REACTIVATED` | `"Device signed in again"` |
| `DEVICE_REVOKED` | `DEVICE_REVOKED` | `"Device removed"` |
| `DEVICE_REPLACED` | `DEVICE_REPLACED` | `"Device replaced"` |
| `DEVICE_LIMIT_REACHED` | `DEVICE_LIMIT_REACHED` | `"Sign-in blocked: maximum active devices reached"` |
| `AUTHN_LOGIN_FAILED` | `LOGIN_FAILED` | `"Sign-in attempt failed"` |
| `SECURITY_TOKEN_REUSE` | `SUSPICIOUS_ACTIVITY_BLOCKED` | `"Suspicious sign-in attempt blocked"` |
| `SESSION_LOGOUT` | `LOGOUT` | `"Signed out"` |
| `SESSION_LOGOUT_ALL` | `LOGOUT_ALL` | `"Signed out of all devices"` |

---

## 15. Anti-Enumeration & Failed Login UX

To prevent attackers from gaining insights through login history:
1. Failed attempts display generic, non-revealing messages (`"Sign-in attempt failed"`).
2. Failed login entries do **NOT** reveal whether an account existed, whether an OTP was requested, or whether a specific device identifier was recognized.
3. Unauthenticated requests to `/api/v1/account/login-history` return standard `401 Unauthorized`.

---

## 16. Account Ownership & IDOR Protections

- **Strict Scoping**: The query layer extracts `userId` **ONLY** from the authenticated `CommunityOttPrincipal` bound to the verified JWT.
- **No Path Parameters**: The endpoint URL is `/api/v1/account/login-history`. It accepts **NO** `userId` path or query parameter.
- **Cross-User Isolation**: User A can NEVER query User B's login history.

---

## 17. User Account Deletion & GDPR Policy

When a user requests account deletion:
1. **`user_login_history`**: Hard-deleted immediately (`ON DELETE CASCADE` via foreign key to `users.id`).
2. **`security_audit_events`**: Anonymized (`user_id` set to `NULL` via `ON DELETE SET NULL`) to preserve audit trail integrity for compliance and SIEM analysis without retaining PII.

---

## 18. Rate Limiting Policy

- **Endpoint**: `GET /api/v1/account/login-history`
- **Rate Limit**: **30 requests per minute** per authenticated user.
- **Exceeded Behavior**: Returns HTTP `429 Too Many Requests` with header `Retry-After: 60`.

---

## 19. Account Security Actions Integration

Rather than duplicating APIs:
- **"Sign Out All Devices"** in UI routes to existing `POST /api/v1/auth/logout-all` (Phase A).
- **"Revoke Specific Device"** in UI routes to existing `POST /api/v1/devices/{id}/revoke` (Phase B.2).

---

## 20. iOS Client Specifications & Localization

### Navigation Flow
`Profile` → `Account Settings` → `Security & Login Activity`

### Localization Tokens (English & Telugu)
```swift
// Localizable.strings (English)
"login_history_title" = "Recent Login Activity";
"current_device_badge" = "CURRENT DEVICE";
"status_success" = "SUCCESS";
"status_failed" = "FAILED";
"status_blocked" = "BLOCKED";
"action_logout_all" = "Sign Out All Devices";

// Localizable.strings (Telugu)
"login_history_title" = "ఇటీవలి లాగిన్ సత్వరచర్యలు";
"current_device_badge" = "ప్రస్తుత పరికరం";
"status_success" = "విజయం";
"status_failed" = "విఫలమైంది";
"status_blocked" = "నిరోధించబడింది";
"action_logout_all" = "అన్ని పరికరాల నుండి నిష్క్రమించు";
```

---

## 21. Future Security Notifications Contract

Phase C.3 establishes the event payload contract for future push/email notifications:
```json
{
  "notification_type": "NEW_DEVICE_LOGIN_ALERT",
  "user_id": 4821,
  "title": "New Sign-in Detected",
  "body": "Your account was accessed from a new iPhone 15 Pro in Hyderabad, India.",
  "occurred_at": "2026-08-21T22:30:00Z"
}
```

---

## 22. Implementation Test Plan (24 Scenarios)

1. **User History Retrieval**: User can fetch their own login history.
2. **Unauthenticated Access**: `GET /api/v1/account/login-history` without JWT returns 401.
3. **Cross-User Isolation**: User A cannot view User B's history.
4. **Pagination**: Correct pagination metadata (`page`, `size`, `total_pages`, `has_next`).
5. **Date Filtering**: Filtering with `from` and `to` parameters works accurately.
6. **Event Filtering**: Filtering by `eventType` returns matching items only.
7. **Platform Filtering**: Filtering by `platform` (iOS/Android/Web) works correctly.
8. **New Device Representation**: `DEVICE_REGISTERED` correctly displays `"Signed in on new device"`.
9. **Failed Login Representation**: `AUTHN_LOGIN_FAILED` displays `"Sign-in attempt failed"`.
10. **Device Revocation Representation**: `DEVICE_REVOKED` displays `"Device removed"`.
11. **Device Replacement Representation**: `DEVICE_REPLACED` displays `"Device replaced"`.
12. **Logout Representation**: `SESSION_LOGOUT` displays `"Signed out"`.
13. **Current Device Calculation**: `is_current_device` returns `true` only for the requesting session's device.
14. **Raw IP Protection**: Raw IP is masked (`192.168.x.x`); raw IP is never exposed.
15. **User-Agent Sanitization**: Raw User-Agent string is omitted; normalized platform/OS shown.
16. **Device Identifier Protection**: Raw DB `device_identifier` UUID is omitted.
17. **Token/Secret Protection**: Tokens, hashes, and OTP values are absent from DTO.
18. **Anti-Enumeration**: Failed login messages give no indication of account existence.
19. **Event Deduplication**: Simultaneous login events map to a single user-facing entry.
20. **Retention Purge**: Records older than 90 days are purged from `user_login_history`.
21. **User Deletion Impact**: Account deletion hard-deletes `user_login_history` rows.
22. **Rate Limiting**: Requests exceeding 30/min return 429 Too Many Requests.
23. **Stable Ordering**: Results ordered strictly by `occurred_at DESC`.
24. **Concurrent Requests**: Concurrent queries execute safely without deadlocks.

---

## 23. Final Architecture Decision

```
===================================================================
DESIGN APPROVED FOR IMPLEMENTATION
===================================================================
```

The design for Phase C.3 (User-Facing Login History & Account Security) is fully specified, verified, and **APPROVED FOR IMPLEMENTATION**.
