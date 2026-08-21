# CommunityOTT Phase C.5B Security Alerts Implementation Documentation

## 1. Architecture Overview
Phase C.5B implements a production-grade, asynchronous, user-facing security alert and notification system for CommunityOTT.

```
Security Event (C.2)
       │
       ▼  [AFTER_COMMIT]
SecurityAlertEventListener (@Async "securityAuditExecutor")
       │
       ▼  [Propagation.REQUIRES_NEW]
SecurityAlertService
       ├── Idempotency Check (source_event_id + processedEventIds)
       ├── 5-Minute Window Deduplication (Except CRITICAL)
       └── Database Persistence (security_alerts)
       │
       ▼
AccountSecurityAlertController (GET /alerts, GET /unread-count, POST /read, POST /read-all)
```

---

## 2. Database Schema (`V23__create_security_alerts.sql`)

```sql
CREATE TABLE security_alerts (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    alert_type          VARCHAR(64) NOT NULL,
    severity            VARCHAR(32) NOT NULL,
    title               VARCHAR(128) NOT NULL,
    message             VARCHAR(255) NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'UNREAD',
    source_event_id     UUID,
    device_id           BIGINT REFERENCES devices(id) ON DELETE SET NULL,
    platform            VARCHAR(32),
    masked_ip           VARCHAR(64),
    approx_location     VARCHAR(128),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    read_at             TIMESTAMPTZ,
    expires_at          TIMESTAMPTZ NOT NULL,
    
    CONSTRAINT chk_alert_severity CHECK (severity IN ('INFO', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT chk_alert_status CHECK (status IN ('UNREAD', 'READ', 'ARCHIVED'))
);

CREATE INDEX idx_security_alerts_user_status_created ON security_alerts (user_id, status, created_at DESC, id DESC);
CREATE INDEX idx_security_alerts_user_created ON security_alerts (user_id, created_at DESC, id DESC);
CREATE INDEX idx_security_alerts_expires ON security_alerts (expires_at);
CREATE UNIQUE INDEX unq_security_alerts_user_event ON security_alerts (user_id, alert_type, source_event_id) WHERE source_event_id IS NOT NULL;
```

---

## 3. Security Event Taxonomy & User Microcopy

| Alert Type | Source Event | Severity | Friendly User Title | Friendly User Message |
| :--- | :--- | :--- | :--- | :--- |
| **`ALERT_NEW_DEVICE`** | `DEVICE_REGISTERED` | **MEDIUM** | New device signed in | *"A new device was used to sign in to your CommunityOTT account."* |
| **`ALERT_DEVICE_REPLACED`** | `DEVICE_REPLACED` | **HIGH** | Device replaced | *"One of your registered devices was replaced."* |
| **`ALERT_SUSPICIOUS_LOGIN`**| `AUTHN_LOGIN_FAILED` / `AUTHN_OTP_FAILED` | **HIGH** | Unusual sign-in activity | *"We detected unusual activity while signing in to your account."* |
| **`ALERT_TOKEN_REUSE`** | `SECURITY_TOKEN_REUSE` | **CRITICAL** | Account security alert | *"We detected unusual activity involving your account session. Please review your recent account activity."* |

---

## 4. Anti-IDOR & Privacy Controls

1. **Anti-IDOR Authorization**: Every controller endpoint strictly resolves user identity from `@AuthenticationPrincipal CommunityOttPrincipal principal.getUserId()`. Request parameters containing arbitrary `userId`s are ignored and never trusted.
2. **Data Minimization**: Secret keys, access tokens, refresh tokens, token hashes, `device_identifier` UUIDs, session IDs, request IDs, trace IDs, raw IP addresses, and User-Agent headers are strictly omitted from `SecurityAlertResponse`.
3. **IP Masking**: Enforces standard masking (`192.168.x.x` / `2001:db8::xxxx`).

---

## 5. REST API Contracts

### `GET /api/v1/account/security/alerts`
- **Query Params**: `status` (`UNREAD`/`READ`/`ARCHIVED`), `page` (default 0), `size` (default 20, max 50).
- **Response**: `ApiResponse<Page<SecurityAlertResponse>>` ordered by `createdAt DESC, id DESC`.

### `GET /api/v1/account/security/alerts/unread-count`
- **Response**: `ApiResponse<UnreadCountResponse>` (`{ "unreadCount": 1 }`).

### `POST /api/v1/account/security/alerts/{alertId}/read`
- **Response**: `ApiResponse<SecurityAlertResponse>` (sets `status = READ` and `readAt = Instant.now()`). Returns 404 if `alertId` does not belong to the authenticated user.

### `POST /api/v1/account/security/alerts/read-all`
- **Response**: `ApiResponse<Void>` (bulk marks all unread alerts for authenticated user as read).

---

## 6. Retention Policy & Expired Purging

- **Retention Window**: User-facing security alerts are persisted with `expires_at = created_at + INTERVAL '60 days'`.
- **Cleanup Method**: `SecurityAlertService.purgeExpiredAlerts()` executes database deletion for records where `expires_at < NOW()`.

---

## 7. Verification & Test Suite Summary

- **Focused Integration Tests**: `SecurityAlertTest.java` (25 / 25 tests passing).
- **Full System Regression Suite**: **512 / 512 tests passing** (0 failures, 0 errors, 0 skipped).
- **Build Status**: `BUILD SUCCESS`.
