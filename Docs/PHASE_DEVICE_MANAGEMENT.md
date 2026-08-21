# CommunityOTT — Phase B.2: Device Management & Session Device Binding Documentation

---

## 1. High-Level Architectural Summary

Phase B.2 completes the Device Domain and Session-Device Binding architecture for the CommunityOTT platform.
It introduces an authoritative, server-managed `Device` entity to model hardware/installation identity, separate from transient server-side authentication sessions (`AuthSession`).

### Key Capabilities Introduced:
- **Dedicated Device Domain**: Server-side tracking of registered client installations.
- **Strict 2-Device Limit per Account**: Enforces a maximum of 2 active registered devices simultaneously per user account.
- **Pessimistic Locking**: Uses `SELECT u FROM User u WHERE u.id = :id FOR UPDATE` during OTP verification to prevent concurrent device registration race conditions.
- **Session-Device Binding**: Foreign Key constraint (`auth_sessions.device_entity_id -> devices.id`) linking every login session to an authoritative device.
- **Cascading Device Revocation**: Revoking a device immediately invalidates all associated server-side authentication sessions (`revoked_at = NOW()`) and rejects subsequent access and refresh tokens.
- **Soft-Deletion & Reactivation**: Revoking a device sets `revoked_at = NOW()`. Re-logging in from a previously revoked device reactivates the existing database row (`revoked_at = NULL`, `last_active_at = NOW()`) to honor `UNIQUE(user_id, device_identifier)` without duplicate key violations.
- **Atomic Device Replacement (`replaceDeviceId`)**: When an account reaches 2 active devices, the user can swap out an existing device by providing `replaceDeviceId` in `POST /api/v1/auth/otp/verify`.

---

## 2. Database Schema Details

### Flyway Migration: `V20__create_devices_and_link_auth_sessions.sql`

```sql
-- 1. Create devices table
CREATE TABLE devices (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_identifier VARCHAR(255) NOT NULL,
    platform VARCHAR(50) NOT NULL,
    device_model VARCHAR(255),
    os_version VARCHAR(100),
    app_version VARCHAR(50),
    display_name VARCHAR(255) NOT NULL,
    first_registered_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_active_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_user_device_identifier UNIQUE (user_id, device_identifier)
);

CREATE INDEX idx_devices_user_id ON devices(user_id);
CREATE INDEX idx_devices_user_revoked ON devices(user_id, revoked_at);
CREATE INDEX idx_devices_identifier ON devices(device_identifier);

-- 2. Link auth_sessions to devices
ALTER TABLE auth_sessions
ADD COLUMN device_entity_id BIGINT REFERENCES devices(id) ON DELETE SET NULL;

CREATE INDEX idx_auth_sessions_device_entity_id ON auth_sessions(device_entity_id);
```

---

## 3. Core Domain Architecture

```
                               ┌─────────────────────────┐
                               │         User            │
                               └────────────┬────────────┘
                                            │ 1
                                            │
                                            │ *
                               ┌────────────▼────────────┐
                               │        Device           │
                               └────────────┬────────────┘
                                            │ 1
                                            │
                                            │ *
                               ┌────────────▼────────────┐
                               │      AuthSession        │
                               └─────────────────────────┘
```

---

## 4. REST API Endpoints

### 1. `GET /api/v1/devices`
Retrieves all registered active/recent devices for the authenticated user.

- **Headers**: `Authorization: Bearer <access_token>`
- **Response**: `HTTP 200 OK`
```json
{
  "success": true,
  "data": [
    {
      "id": 15,
      "deviceIdentifier": "device-uuid-1",
      "displayName": "Hardik's iPhone",
      "platform": "IOS",
      "deviceModel": "iPhone 15 Pro",
      "osVersion": "iOS 17.4",
      "appVersion": "1.4.0",
      "firstRegisteredAt": "2026-08-21T21:40:00Z",
      "lastActiveAt": "2026-08-21T21:40:00Z",
      "revokedAt": null,
      "isCurrentDevice": true,
      "status": "ACTIVE"
    }
  ],
  "message": "Devices retrieved successfully",
  "timestamp": "2026-08-21T21:40:00Z"
}
```

### 2. `POST /api/v1/devices/{deviceId}/revoke`
Revokes a specific registered device and invalidates all associated authentication sessions.

- **Headers**: `Authorization: Bearer <access_token>`
- **Response**: `HTTP 200 OK`
```json
{
  "success": true,
  "data": {
    "deviceId": 15,
    "status": "REVOKED",
    "revokedSessionsCount": 2,
    "revokedAt": "2026-08-21T21:40:05Z"
  },
  "message": "Device revoked successfully",
  "timestamp": "2026-08-21T21:40:05Z"
}
```

---

## 5. Verification Matrix & Scenarios Covered

| # | Test Scenario | Verified | Description |
|---|---|---|---|
| 1 | First Device Registration | ✅ PASS | Registers first device, creates session & links `device_entity_id`. |
| 2 | Second Device Registration | ✅ PASS | Registers second device, active count = 2. |
| 3 | Third Device Blocked (409) | ✅ PASS | Rejects 3rd device attempt with `409 MAX_DEVICES_REACHED` carrying active devices list. |
| 4 | Existing Device Re-Login | ✅ PASS | Re-login from registered device updates `last_active_at` without increasing device count. |
| 5 | Device Reactivation | ✅ PASS | Re-login from revoked device reactivates existing database row if active count < 2. |
| 6 | Device Replacement (`replaceDeviceId`) | ✅ PASS | Atomically revokes specified device ID and registers new device under pessimistic lock. |
| 7 | Device Revocation Cascade | ✅ PASS | POST `/api/v1/devices/{id}/revoke` sets `revoked_at` on device and all linked `AuthSession` rows. |
| 8 | Revoked Token Access Blocked | ✅ PASS | Requests with access/refresh tokens from a revoked device return `401 Unauthorized`. |
| 9 | Device Listing Endpoint | ✅ PASS | GET `/api/v1/devices` returns user's devices and accurately flags `isCurrentDevice`. |
| 10 | Cross-User Security (IDOR) | ✅ PASS | Attempting to revoke another user's device returns `404 Not Found`. |
| 11 | Validation Rules | ✅ PASS | Missing or blank `deviceId` returns `400 Bad Request`. |
| 12 | Logout Isolation | ✅ PASS | Logging out of a single session revokes that session but leaves the registered device active. |

---

## 6. Verification Status

- **Device Management Test Suite**: `10 / 10 Tests Passing`
- **Core Authentication Test Suites**: `42 / 42 Tests Passing`
- **Git Operations**: 0 commits, 0 pushes. Strict git hygiene maintained.
