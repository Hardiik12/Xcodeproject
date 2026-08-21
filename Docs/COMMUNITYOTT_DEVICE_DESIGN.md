# CommunityOTT Device Management & Session Binding Architecture

## 1. Executive Summary

This document specifies the finalized architecture, data model, lifecycle states, transactional concurrency defense, API specifications, and test strategy for **Device Management & Session-Device Binding** in the CommunityOTT platform.

This design has been reviewed and **APPROVED FOR IMPLEMENTATION**.

---

## 2. Device Identity Concepts

CommunityOTT distinguishes between two different device identity handles:

| Identity Handle | Scope & Storage | Purpose & Characteristics |
| :--- | :--- | :--- |
| **Server Device ID** (`devices.id`) | Database Primary Key (`BIGINT`), generated server-side. | Authoritative internal handle used in REST APIs (e.g. `/api/v1/devices/{deviceId}/revoke`) and as Foreign Key in `auth_sessions.device_entity_id`. Opaque to external clients. |
| **Client Installation ID** (`device_identifier`) | Generated and stored locally on the client (e.g. iOS Keychain UUID). | Passed in OTP verify / login request payload to identify a specific client app installation. Never treated as a secret credential or authorization bearer token. |

### Prohibited Device Identity Handles
IP addresses, User-Agent strings, browser canvas/audio fingerprints, and MAC addresses MUST NEVER be used as primary device identity attributes.

---

## 3. Device Lifecycle & Reactivation Policy

### State Model
```
            ┌─────────────────────────────┐
            │          REGISTERED         │
            └──────────────┬──────────────┘
                           │
            ┌──────────────▼──────────────┐
            │            ACTIVE           │
            │     (revoked_at IS NULL)    │
            └──────┬───────────────▲──────┘
                   │               │
       Device Revocation     Re-Login from
        or Replacement       Same Device
                   │               │
            ┌──────▼───────────────┴──────┐
            │           REVOKED           │
            │    (revoked_at IS NOT NULL) │
            └─────────────────────────────┘
```

### Soft-Deletion & Reactivation Decision
1. **Historical Auditability (Soft-Deletion)**: Revoked devices are retained in the `devices` table with `revoked_at = timestamp` for security auditing and historical tracking.
2. **Reactivation Rule**: The database enforces a uniqueness constraint `UNIQUE(user_id, device_identifier)`. If a user logs in again from a previously revoked installation (`device_identifier`), the system **REACTIVATES the existing row** (`revoked_at = null`, `last_active_at = now()`) rather than attempting a duplicate `INSERT`.

---

## 4. AuthSession ↔ Device Relational Model

```sql
User (1) ───< Devices (*) ───< AuthSessions (*)
```

### Schema Rules
- `auth_sessions.device_entity_id` is a Foreign Key referencing `devices(id)`.
- `device_entity_id` is nullable ONLY for historical legacy sessions created prior to Flyway V20 migration. All newly issued sessions MUST link to a `Device`.
- Every `AuthSession` belongs to the exact same `User` as its parent `Device`.
- **Cascade Revocation**: Revoking a `Device` sets `revoked_at = now()` on ALL active `AuthSession` records belonging to that device.
- **Session Logout**: Logging out an individual session (`POST /api/v1/auth/logout`) revokes the `AuthSession`, but DOES NOT delete or revoke the parent `Device`.

---

## 5. Two-Device Rule & Concurrency Lock Strategy

CommunityOTT enforces a strict business rule of **maximum 2 active registered devices per user account**.

### Execution Flow
```
Login Request (OTP Verify)
          ↓
Lookup (user_id, device_identifier) in DB
          ↓
     Device Exists & Active?
    ├── YES ──> Update last_active_at → Issue AuthSession (SUCCESS)
    └── NO  ──> Acquire Pessimistic Lock on User (SELECT ... FOR UPDATE)
                      ↓
                Count Active Devices (revoked_at IS NULL)
                      ↓
                 Count < 2?
                ├── YES ──> Reactivate or Insert Device → Issue AuthSession (SUCCESS)
                └── NO  ──> Throw MaxDevicesReachedException (409 CONFLICT)
                            Return List of 2 Active Devices for Swap UX
```

### Concurrency Protection
To prevent race conditions where simultaneous login requests from Device C and Device D both see `count = 1` and register 3 devices:
- `userRepository.findByIdForUpdate(userId)` executes `SELECT u FROM User u WHERE u.id = :userId FOR UPDATE`.
- This forces concurrent device registration transactions for the same user to execute serially.

---

## 6. Device Replacement (Swap) Flow

When a user with 2 active devices attempts to log in from a 3rd device:

1. **409 Conflict Response**: The server rejects session creation with error `MAX_DEVICES_REACHED` and returns the list of current active devices (ID, display name, model, last active timestamp).
2. **User Selection**: User chooses a device to replace (e.g. `replaceDeviceId: 101`).
3. **Atomic Swap Endpoint / Payload**:
   Call `POST /api/v1/auth/otp/verify` with `replaceDeviceId: 101`.
4. **Atomic Transaction**:
   - Lock `User` (`SELECT FOR UPDATE`).
   - Verify `replaceDeviceId` belongs to user and is active.
   - Revoke `replaceDeviceId` (`revoked_at = now()`).
   - Revoke all `AuthSession` records linked to `replaceDeviceId`.
   - Register/Reactivate new Device C.
   - Issue `AuthSession` for Device C.
   - Commit transaction.

---

## 7. Device Revocation Design

### Endpoint
`POST /api/v1/devices/{deviceId}/revoke`

### Transaction Contract
- **Authentication**: Requires valid Bearer JWT.
- **Ownership Validation**: Verifies `device.getUserId().equals(principal.getUserId())`. Throws HTTP 404/403 if mismatched to prevent IDOR / device enumeration.
- **Actions**:
  1. `UPDATE devices SET revoked_at = NOW() WHERE id = :deviceId AND user_id = :userId`
  2. `UPDATE auth_sessions SET revoked_at = NOW() WHERE device_entity_id = :deviceId AND revoked_at IS NULL`
  3. Invalidate refresh token state in DB.
- **Immediate Outcome**: Any subsequent API request using a JWT access token bound to a session of that device is immediately rejected with HTTP 401 Unauthorized via `JwtAuthenticationFilter`.

---

## 8. Device List API Design

### Endpoint
`GET /api/v1/devices`

### JSON Response Specification
```json
{
  "success": true,
  "message": "Devices retrieved successfully",
  "data": [
    {
      "id": 101,
      "platform": "IOS",
      "deviceModel": "iPhone 15 Pro",
      "osVersion": "iOS 17.4",
      "appVersion": "1.4.0",
      "displayName": "Hardik's iPhone",
      "firstRegisteredAt": "2026-08-01T10:00:00Z",
      "lastActiveAt": "2026-08-21T21:30:00Z",
      "isCurrentDevice": true,
      "status": "ACTIVE"
    },
    {
      "id": 102,
      "platform": "TABLET",
      "deviceModel": "iPad Air 5th Gen",
      "osVersion": "iPadOS 17.2",
      "appVersion": "1.3.9",
      "displayName": "Living Room iPad",
      "firstRegisteredAt": "2026-08-10T14:20:00Z",
      "lastActiveAt": "2026-08-19T18:45:00Z",
      "isCurrentDevice": false,
      "status": "ACTIVE"
    }
  ],
  "timestamp": "2026-08-21T21:36:00Z"
}
```

---

## 9. Current Device Detection

The backend identifies whether a listed device is the client's current device via session context:

```
Authenticated HTTP Request (JWT Bearer)
              ↓
Extract sid (Session ID) in JwtAuthenticationFilter
              ↓
Load AuthSession from DB (authSession.getDeviceEntity().getId())
              ↓
isCurrentDevice = (device.getId().equals(currentAuthSession.getDeviceEntity().getId()))
```

---

## 10. iOS Installation Identifier Strategy

1. **Keychain Persistence**:
   - On first launch, the iOS app checks the system Keychain (`kSecClassGenericPassword`) for `com.communityott.device_uuid`.
   - If missing, it generates a random `UUID().uuidString` and writes it to the Keychain with `kSecAttrAccessibleAfterFirstUnlock`.
2. **Persistence Guarantee**:
   - Persists across app launches, user logouts, and app reinstalls (Keychain items under the developer Team ID survive app deletion).
3. **App Store Compliance**:
   - Fully compliant with Apple App Store Guidelines Section 5.1.1 (Privacy). Does not access IDFA or invasive device hardware serials.

---

## 11. Complete Flyway V20 Migration & Schema

```sql
-- Flyway Migration: V20__create_devices_and_link_auth_sessions.sql

CREATE TABLE devices (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_identifier VARCHAR(255) NOT NULL,
    platform VARCHAR(32) NOT NULL,
    device_model VARCHAR(255),
    os_version VARCHAR(64),
    app_version VARCHAR(64),
    display_name VARCHAR(255),
    first_registered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_active_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_user_device_identifier UNIQUE (user_id, device_identifier)
);

CREATE INDEX idx_devices_user_active ON devices(user_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_devices_identifier ON devices(device_identifier);

-- Add Foreign Key to auth_sessions
ALTER TABLE auth_sessions ADD COLUMN device_entity_id BIGINT REFERENCES devices(id) ON DELETE CASCADE;
CREATE INDEX idx_auth_sessions_device_entity ON auth_sessions(device_entity_id);
```

---

## 12. Security Specification

- **IDOR Protection**: All device endpoints check ownership (`device.getUserId().equals(principal.getUserId())`).
- **Device Enumeration Defense**: Mismatched device lookup throws HTTP 404/403 to obscure existence.
- **Race Condition Defense**: `SELECT FOR UPDATE` on `users` table serializes concurrent registration transactions.
- **Immediate Invalidation**: Device revocation cascades immediately to active `auth_sessions` and refresh tokens.

---

## 13. API Endpoint Summary

| Method | Endpoint | Description | Auth Required |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/devices` | List registered devices for current user | Yes (JWT) |
| `POST` | `/api/v1/devices/{deviceId}/revoke` | Revoke specific device and all its active sessions | Yes (JWT) |
| `POST` | `/api/v1/auth/otp/verify` | Verify OTP, register/reactivate device, issue session (supports `replaceDeviceId`) | No |

---

## 14. Implementation Contract & Test Plan (14 Scenarios)

When Phase B implementation begins, the test suite must verify:

1. **First Device Registration**: Registering first device succeeds (`count = 1`).
2. **Second Device Registration**: Registering second device succeeds (`count = 2`).
3. **Third Device Rejection**: Registering third device fails with HTTP 409 `MAX_DEVICES_REACHED`.
4. **Existing Device Login**: Login from registered active Device A updates `last_active_at` without incrementing device count.
5. **Device Swap**: Login with `replaceDeviceId = Device B` revokes Device B and registers Device C.
6. **Device Revocation Cascade**: Revoking Device A revokes all associated `AuthSession` records.
7. **Access Token Invalidation**: Access token for revoked device returns HTTP 401 on next API request.
8. **Refresh Token Invalidation**: Refresh token for revoked device fails (HTTP 401).
9. **Logout-All Cascade**: `logout-all` revokes all active devices and sessions for user.
10. **Cross-User Isolation**: User A cannot view or revoke User B's device (HTTP 404/403).
11. **Concurrency Protection**: Concurrent login attempts from Device C and Device D under `FOR UPDATE` lock result in exactly 2 active devices.
12. **Reactivation**: Logging in again from previously revoked Device A reactivates Device A row instead of throwing unique constraint violation.
13. **Current Device Flag**: `GET /api/v1/devices` correctly marks `isCurrentDevice = true` for the requesting session's device.
14. **Validation**: Blank or malformed `device_identifier` returns HTTP 400 Bad Request.

---

## 15. Architectural Approval Verdict

### Verdict
**DESIGN APPROVED FOR IMPLEMENTATION**

All design reviews, schemas, concurrency lock models, privacy audits, and API contracts are finalized and ready for development in Phase B.2.
