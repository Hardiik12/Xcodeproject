# CommunityOTT Phase C.5A Security Alerts & User Notification Architecture Design

## 1. Existing System Inspection

### Codebase Inventory

| Infrastructure Category | Components | Status | Notes |
| :--- | :--- | :--- | :--- |
| **Audit Infrastructure** | `SecurityAuditEventPublisher`, `SecurityAuditEventListener`, `security_audit_events` | **EXISTING (C.2)** | Full immutable event ledger; `AFTER_COMMIT` publishing semantics |
| **User History Projection**| `UserLoginHistoryProjectionService`, `user_login_history` | **EXISTING (C.3)** | User-facing privacy-sanitized login activity table |
| **Async Executors** | `securityAuditExecutor` (`@Async` thread pool) | **EXISTING** | Configured in `SecurityAuditConfig` |
| **Notification Entities** | `security_alerts` / Notification entities | **MISSING** | Package `com.communityott.notification` contains only `package-info.java` |
| **Notification Services** | `SecurityAlertService` / `NotificationDispatcher` | **MISSING** | To be implemented in Phase C.5B |
| **Email Infrastructure** | `JavaMailSender` / SendGrid / Amazon SES | **MISSING** | No email provider dependency or configuration |
| **Push Infrastructure** | APNs (Apple Push) / Firebase Cloud Messaging (FCM) | **MISSING** | No push provider SDK or device token mapping |
| **SMS Infrastructure** | Twilio / AWS SNS | **MISSING** | No SMS provider integration |
| **User Preferences** | `user_notification_preferences` | **MISSING** | No preference toggle storage |

---

## 2. Alert Domain Model

### Schema Design (`V23__create_security_alerts.sql`)

```sql
CREATE TABLE security_alerts (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    alert_type          VARCHAR(64) NOT NULL,
    severity            VARCHAR(32) NOT NULL,
    title               VARCHAR(128) NOT NULL,
    message             VARCHAR(255) NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'UNREAD',
    source_event_id     UUID REFERENCES security_audit_events(id) ON DELETE SET NULL,
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

CREATE INDEX idx_security_alerts_user_status_created ON security_alerts (user_id, status, created_at DESC);
CREATE INDEX idx_security_alerts_user_created ON security_alerts (user_id, created_at DESC);
CREATE INDEX idx_security_alerts_expires ON security_alerts (expires_at);
```

### Field Rationale
- `source_event_id`: Links back to the underlying immutable C.2 audit event for traceability without duplicating full audit payloads.
- `status`: Tracks user interaction state (`UNREAD`, `READ`, `ARCHIVED`).
- `expires_at`: Enforces retention boundaries (default: 60 days after `created_at`).

---

## 3. Alert Taxonomy & User Microcopy

| Alert Type | Trigger Condition | Severity | Friendly User Title | Friendly User Message |
| :--- | :--- | :--- | :--- | :--- |
| **`ALERT_NEW_DEVICE`** | Sign-in on a newly registered `Device` | **MEDIUM** | New Sign-In Detected | *"Your CommunityOTT account was accessed from a new device (iPhone 15 Pro)."* |
| **`ALERT_DEVICE_REPLACED`** | Active device replacement executed | **HIGH** | Device Replaced | *"A registered device was replaced by 'Chrome Web Browser'. If this wasn't you, review your account security immediately."* |
| **`ALERT_SUSPICIOUS_LOGIN`**| Failed login attempts followed by success or impossible travel | **HIGH** | Unusual Account Activity | *"Unusual sign-in activity was detected on your account. Please review your active devices."* |
| **`ALERT_TOKEN_REUSE`** | Revoked refresh token presented | **CRITICAL** | Security Action Taken | *"For your protection, all active sessions were signed out due to an unusual security event."* |

> [!CAUTION]
> **Forbidden Terms in User Microcopy**: Never expose `TOKEN_REUSE`, `JWT`, `sid`, `refresh_token_hash`, `device_identifier`, `trace_id`, `request_id`, or raw stack trace errors to the user.

---

## 4. Severity Model & Channel Routing

```
   CRITICAL  │ In-App + Email + Push + SMS  (Mandatory, Non-bypassable)
   HIGH      │ In-App + Email + Push        (User configurable Push)
   MEDIUM    │ In-App + Email               (User configurable Email)
   LOW / INFO│ In-App Only                  (Silent in UI)
```

| Severity Level | Definition | Default Target Channels | Bypass User Suppression? |
| :---: | :--- | :--- | :---: |
| **INFO** | Informational security status updates | In-App | No |
| **LOW** | Minor non-actionable account activity | In-App | No |
| **MEDIUM** | Recognized new device registration | In-App, Email | No |
| **HIGH** | Device replacement, multiple failures | In-App, Email, Push | No |
| **CRITICAL**| Compromise detected (Token reuse) | In-App, Email, Push, SMS | **YES (Mandatory Security Notice)** |

---

## 5. Minimum In-App REST API Surface

All endpoints strictly resolve `userId` from the authenticated `@AuthenticationPrincipal CommunityOttPrincipal principal` (Anti-IDOR). No `userId` parameter allowed in requests.

1. **`GET /api/v1/account/security/alerts`**
   - Retrieves paginated user security alerts (default `page=0`, `size=20`, filterable by `status`).
2. **`GET /api/v1/account/security/alerts/unread-count`**
   - Lightweight unread badge counter for mobile menu headers.
3. **`POST /api/v1/account/security/alerts/{alertId}/read`**
   - Marks a specific alert as `READ`.
4. **`POST /api/v1/account/security/alerts/read-all`**
   - Bulk marks all `UNREAD` alerts for the authenticated user as `READ`.

---

## 6. Privacy & Data Sanitization Standards

| Data Attribute | In-App & Email Payload Format | Storage Rules |
| :--- | :--- | :--- |
| **IP Address** | `192.168.x.x` (IPv4) / `2001:db8::xxxx` (IPv6) | Only masked IP stored in `security_alerts` |
| **Location** | `"Hyderabad, India"` or `"Local Network"` | Coarse location string derived from IP |
| **Device Name** | `"iPhone 15 Pro"` or `"Safari Web Browser"` | Human-friendly display name |
| **Raw Secrets** | **OMITTED** | Zero access tokens, refresh tokens, OTPs, or hashes |
| **Internal IDs** | **OMITTED** | Zero `device_identifier` UUIDs, session `sid`s, or trace IDs |

---

## 7. Deduplication & Idempotency Strategy

### Problem Scenario
During a single new-device login, C.2 emits:
1. `AUTHN_LOGIN_SUCCESS`
2. `DEVICE_REGISTERED`
3. `SESSION_CREATED`

Without deduplication, three identical notifications would be triggered.

### Solution: Event Correlation & Unique Constraint

```sql
-- Idempotency Key Formula: SHA-256(user_id + ":" + alert_type + ":" + source_event_id)
```

1. **Correlation Logic**: `SecurityAlertEvaluator` checks if a `security_alert` record with the same `(user_id, alert_type, source_event_id)` or within a 5-minute window for identical `device_id` already exists.
2. **Suppression**: If existing entry found, additional alert creation is skipped cleanly.
3. **Database Guard**: Partial unique index on active unread alerts for the same source event.

---

## 8. Asynchronous Architecture & Flow Diagram

```
+--------------------------+
| Core Business Operation  |  (Login / Token Refresh / Device Revoke)
+--------------------------+
             |
             v
+--------------------------+
| SecurityAuditEvent       |  (Published via Spring ApplicationEventPublisher)
| Publisher                |
+--------------------------+
             |
             v  [AFTER_COMMIT]
+--------------------------+
| SecurityAlertEventListener| (@Async "securityAuditExecutor")
+--------------------------+
             |
             v
+--------------------------+
| SecurityAlertEvaluator   |  (Evaluates severity, deduplication, & preferences)
+--------------------------+
             |
             v  [Propagation.REQUIRES_NEW]
+--------------------------+
| Persist security_alerts  |  (DB Insertion)
+--------------------------+
             |
             +--------------------------+--------------------------+
             |                          |                          |
             v                          v                          v
   +-------------------+      +-------------------+      +-------------------+
   | InApp Notification|      |  Email Channel    |      | Push Notification |
   | (Web / Mobile)    |      | (JavaMail/SES)    |      |  (FCM / APNs)     |
   +-------------------+      +-------------------+      +-------------------+
```

---

## 9. Failure, Retry, & Outbox Strategy

1. **Transaction Isolation**: Alert evaluation runs in `@Transactional(propagation = Propagation.REQUIRES_NEW)`. A DB insertion failure in `security_alerts` **NEVER** rolls back the user's primary login or token refresh transaction.
2. **Channel Dispatch Fault Tolerance**:
   - `InApp` notification persists synchronously with the `security_alerts` record.
   - `Email` & `Push` dispatches run asynchronously with independent try-catch wrappers.
   - Channel failure logs a warning and marks `channel_status = 'FAILED'`, but does NOT fail the alert creation.
3. **Retry Strategy**: Failed high-priority email/push dispatches are retried up to 3 times using an exponential backoff schedule (10s, 60s, 300s).

---

## 10. Notification Spam Cooldown & Suppression

To prevent notification flooding during automated reconnect loops or client retries:
- **`ALERT_NEW_DEVICE` Cooldown**: Maximum 1 alert per user per device per 15-minute window.
- **`ALERT_SUSPICIOUS_LOGIN` Cooldown**: Maximum 1 alert per user per 10-minute window.
- **CRITICAL Security Bypass**: `ALERT_TOKEN_REUSE` (token theft/replay) **BYPASSES ALL COOLDOWNS** and dispatches immediately on all channels.

---

## 11. User Preferences Boundary

Users may customize notification channels under **Account -> Settings -> Notifications**:
- **Allowed Toggles**:
  - New Device Sign-In Alerts (Email: ON/OFF, Push: ON/OFF)
  - Device Replacement Alerts (Push: ON/OFF)
- **Non-Bypassable Mandates**:
  - Critical Account Security & Token Reuse alerts (`ALERT_TOKEN_REUSE`) **CANNOT BE DISABLED**. Email notifications remain permanently forced `ON`.

---

## 12. Account Security Screen Wireframe (iOS / Web UI)

```
+-------------------------------------------------------------------+
|  < Back             SECURITY ALERTS                               |
+-------------------------------------------------------------------+
|                                                                   |
|  UNREAD ALERTS (1)                                                |
|                                                                   |
|  [!] Security Action Taken                              CRITICAL  |
|      All active sessions were signed out for your safety.        |
|      Masked IP: 192.168.x.x • 10 minutes ago                      |
|      [Mark as Read]                                               |
|                                                                   |
|  RECENT ALERTS                                                    |
|                                                                   |
|  [i] New Sign-In Detected                               MEDIUM    |
|      Signed in on iPhone 15 Pro (Hyderabad, India).              |
|      Today at 4:15 PM                                             |
|                                                                   |
|  [i] Device Replaced                                    HIGH      |
|      'Chrome Web Browser' replaced 'Living Room TV'.              |
|      Yesterday at 11:30 AM                                        |
|                                                                   |
|  [ Mark All as Read ]                                             |
+-------------------------------------------------------------------+
```

---

## 13. Retention & Cleanup Policy

- **User-Facing Retention**: `security_alerts` records expire **60 days** after creation (`expires_at = created_at + INTERVAL '60 days'`).
- **Automated Purge Job**: Scheduled daily Spring `@Scheduled(cron = "0 0 3 * * ?")` job deletes expired alerts (`DELETE FROM security_alerts WHERE expires_at < NOW()`).

---

## 14. API DTO Specifications

### `LoginAlertItemResponse` DTO

```json
{
  "id": 1024,
  "alert_type": "ALERT_NEW_DEVICE",
  "severity": "MEDIUM",
  "title": "New Sign-In Detected",
  "message": "Your CommunityOTT account was accessed from a new device (iPhone 15 Pro).",
  "status": "UNREAD",
  "platform": "IOS",
  "masked_ip": "192.168.x.x",
  "approx_location": "Hyderabad, India",
  "created_at": "2026-08-21T18:30:00Z"
}
```

### `UnreadCountResponse` DTO

```json
{
  "unread_count": 1
}
```

---

## 15. Final Gap Matrix

| Requirement | Existing Capability | Missing Capability | Priority | Recommendation |
| :--- | :--- | :--- | :---: | :--- |
| **Alert Ledger** | C.2 `security_audit_events` | User-facing `security_alerts` table | **P0** | Implement Flyway `V23__create_security_alerts.sql` |
| **In-App API** | C.3 `/account/login-history` | `/account/security/alerts` endpoints | **P0** | Implement `AccountSecurityAlertController` |
| **Alert Engine**| C.2 `@EventListener` | `SecurityAlertEvaluatorService` | **P0** | Implement async event evaluator |
| **Email Dispatch**| None | `EmailNotificationService` & templates | **P1** | Add Spring Mail / SendGrid abstraction |
| **Push Dispatch**| None | `PushNotificationService` & FCM/APNs | **P2** | Add Push notification abstraction |

---

## 16. Implementation Roadmap

1. **Phase C.5A (Current)**: Architecture & Domain Design (**APPROVED**).
2. **Phase C.5B**: Backend Implementation (`V23` migration, `SecurityAlert` entity/repository, `SecurityAlertEvaluatorService`, `AccountSecurityAlertController`, 20+ integration tests).
3. **Phase C.5C**: Final Verification & Full Regression Audit.
4. **Phase C.6**: Enhanced Device Replacement Safeguards (Step-up OTP confirmation).
