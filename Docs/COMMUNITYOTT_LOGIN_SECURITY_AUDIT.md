# CommunityOTT — Phase C.1: Login History & Security Events Audit

---

## 1. Executive Summary & Audit Context

**Project Name**: CommunityOTT iOS & Backend  
**Audit Area**: Login History, Security Event Auditing, Session Lifecycle Tracking, and Device Security Visibility  
**Phase**: Phase C.1 — Audit & Gap Analysis  
**Existing System State**:
- **Phase A**: Passwordless OTP Auth, JWT Access Tokens (15-min TTL), Session-Bound Refresh Tokens, Rotation, Reuse Detection, Logout/Logout-All, Server-Side Session Revocation.
- **Phase B.2**: Registered Device Management, Max 2 Active Devices Limit (pessimistic lock), Session-Device FK binding, Device Revocation Cascade, Device Reactivation, Atomic Device Replacement (`replaceDeviceId`).
- **Verified Regression**: 452/452 tests passing (0 failures, 0 errors).

**Audit Purpose**: Evaluate the current backend and iOS authentication architecture to determine whether login history, security event logging, and account security visibility exist, identify security gaps, and establish a production-grade architecture compliant with OTT standards and OWASP guidelines.

---

## 2. Current Capabilities (What Currently Exists)

### 2.1 Inspection of Authentication & Session Flow

| Event Trigger | Application Component | Current Behavior | Logging / Audit State |
|---|---|---|---|
| **OTP Request** | `OtpService.requestOtp` | Creates `otp_requests` row with purpose, status, expiresAt. Writes to Redis cooldown/rate-limit keys. | **PARTIALLY EXISTS** (DB row in `otp_requests`, transient) |
| **OTP Delivery Attempt** | `OtpService` / Provider | Creates `otp_delivery_attempts` row with status, provider ID, channel. | **PARTIALLY EXISTS** (DB row in `otp_delivery_attempts`) |
| **OTP Verification Success** | `AuthenticationService` | Sets `otp_requests.status = VERIFIED`, creates `AuthSession`, issues JWT. Logs `log.info("Authentication successful...")`. | **PARTIALLY EXISTS** (Operational session created, ephemeral app log) |
| **OTP Verification Failed** | `OtpService.verifyOtp` | Increments `otp_requests.attempt_count`, marks `FAILED` if max attempts reached. | **PARTIALLY EXISTS** (DB attempt counter, no separate security log) |
| **OTP Expired / Invalid** | `OtpService` | Throws `OtpInvalidException` or `OtpExpiredException`. Ephemeral error logs. | **PARTIALLY EXISTS** (DB status set to EXPIRED on cleanup) |
| **Login Success** | `AuthenticationService` | Creates `AuthSession` with IP, User-Agent, refreshTokenHash, expiresAt. | **PARTIALLY EXISTS** (`auth_sessions` row represents active state, not historical log) |
| **Login Failure** | `AuthenticationService` | Throws exception. Handled by `GlobalExceptionHandler`. | **MISSING** (No audit event persisted on failure) |
| **JWT Issued** | `JwtTokenService` | Generates JWT signed with HMAC-SHA256 containing `userId`, `sessionId`, `roles`. | **MISSING** (Token generation is purely functional, no log) |
| **JWT Rejected** | `JwtAuthenticationFilter` | Clears SecurityContext, delegates to `EntryPoint`. Ephemeral debug log. | **MISSING** (No persistent audit event) |
| **Refresh Success** | `AuthenticationService.refreshTokens` | Rotates refresh token, updates `auth_sessions.refresh_token_hash`. | **PARTIALLY EXISTS** (Updates operational `auth_sessions` row, app log) |
| **Refresh Failure** | `AuthenticationService` | Throws `AuthSessionRevokedException` or `AuthSessionExpiredException`. | **MISSING** (No security event log) |
| **Refresh Token Reuse** | `AuthenticationService` | **ALERT TRIGGERED**: Revokes session immediately, logs `log.warn("SECURITY ALERT: Refresh token reuse...")`. | **PARTIALLY EXISTS** (Session revoked, app log warned, NO persistent audit table) |
| **Session Expiration** | Scheduled Purge / Filter | Evaluated dynamically (`expiresAt < NOW()`). | **MISSING** (No event emitted when session expires) |
| **Session Revocation** | `AuthenticationService` | Sets `auth_sessions.revoked_at = NOW()`. | **PARTIALLY EXISTS** (State change on `auth_sessions`) |
| **Logout** | `AuthController.logout` | Revokes specific `AuthSession`. | **PARTIALLY EXISTS** (State change on `auth_sessions`) |
| **Logout-All** | `AuthController.logoutAll` | Revokes all `AuthSession` rows for user. | **PARTIALLY EXISTS** (Bulk state update on `auth_sessions`) |

---

### 2.2 Inspection of Device Security Events

| Event Trigger | Component | Current State | Audit Persistence |
|---|---|---|---|
| **Device Registered** | `DeviceService.resolveOrCreateDevice` | Creates row in `devices` table (`first_registered_at`, `last_active_at`). | **PARTIALLY EXISTS** (Entity created, but no immutable event record) |
| **Existing Device Login** | `DeviceService` | Updates `devices.last_active_at = NOW()`. | **PARTIALLY EXISTS** (Field updated in place, historical logins overwritten) |
| **Device Reactivated** | `DeviceService` | Sets `revoked_at = NULL`, `last_active_at = NOW()`. | **PARTIALLY EXISTS** (Field updated in place) |
| **Device Revoked** | `DeviceService.revokeDevice` | Sets `devices.revoked_at = NOW()`, revokes linked sessions. | **PARTIALLY EXISTS** (Field updated in place) |
| **Device Replaced (`replaceDeviceId`)** | `DeviceService` | Atomically revokes old device, registers new device. | **PARTIALLY EXISTS** (Entity state changes executed, no event record) |
| **Max Devices Reached** | `DeviceService` | Throws `MaxDevicesReachedException` (409 Conflict). | **MISSING** (No security event stored when limit is hit) |
| **Cross-User Device Access (IDOR)** | `DeviceController` | Filtered by `user_id`, returns 404. | **MISSING** (No security alert/audit log created for IDOR attempt) |
| **Concurrent Registration Conflict** | `DeviceService` | Locked via `SELECT FOR UPDATE`, second transaction throws 409. | **MISSING** (No conflict log persisted) |

---

### 2.3 Inspection of Authorization Events

| Event | Component | Current Behavior | Storage State |
|---|---|---|---|
| **401 Unauthorized** | `CustomAuthenticationEntryPoint` | Writes JSON response `UNAUTHORIZED`. | **App Logs Only** (Not in DB) |
| **403 Forbidden** | `CustomAccessDeniedHandler` | Logs `log.debug("Forbidden access attempt...")`. Writes JSON response. | **App Logs Only** (Not in DB) |
| **RBAC Permission Denial** | `RbacAuthorizationService` | Evaluates permission against `role_permissions`. Logs trace. | **App Logs Only** (Not in DB) |
| **IDOR Access Attempt** | Resource Controllers / Services | Throws `ResourceNotFoundException` or `AccessDeniedException`. | **App Logs Only** (Not in DB) |
| **Revoked Session Access** | `JwtAuthenticationFilter` | DB check returns `revoked_at != null`, rejects token. | **App Logs Only** (Not in DB) |
| **Revoked Device Access** | `JwtAuthenticationFilter` | Session linked to revoked device is revoked, rejected. | **App Logs Only** (Not in DB) |

---

### 2.4 Database Audit & Operational vs Audit Tables

An audit of all Flyway migrations (`V1` through `V20`) reveals:
- **Existing Tables**: `users`, `roles`, `permissions`, `user_roles`, `role_permissions`, `otp_requests`, `otp_delivery_attempts`, `auth_sessions`, `devices`.
- **Dedicated Audit / Security Tables**: **NONE**.

#### Architectural Evaluation: Can Operational Tables Support Login History?
**NO**. Reusing operational tables like `auth_sessions` or `devices` as audit logs is an **anti-pattern**:
1. **In-Place Updates Overwrite History**: When a user logs in from an existing device, `devices.last_active_at` is updated in place. Previous login timestamps are lost.
2. **State Revocation vs Event Record**: When an `AuthSession` is revoked or hard-purged during maintenance, historical evidence of that login is destroyed if `auth_sessions` is the only record.
3. **Failed Attempts Unrecorded**: `auth_sessions` and `devices` rows are created ONLY upon successful authentication. Failed logins, invalid OTPs, and token reuse attacks leave no trace in operational tables.

> [!CRITICAL]
> **A dedicated, immutable, append-only security event audit table is required.** Operational state tables (`auth_sessions`, `devices`) must remain separate from event audit streams.

---

## 3. Missing Capabilities & Security Risks

### 3.1 Key Missing Capabilities
1. **No User-Facing Login History Endpoint**: Users cannot view active/recent login history, login locations, or device activity.
2. **No Persistent Failure Audit Trail**: Failed OTP attempts, brute-force attacks, and bad logins leave no queryable database trace.
3. **No Security Alert History**: Refresh-token reuse attacks trigger transient `log.warn` statements but produce no permanent security incident log for admins or automated threat detection.
4. **Overwritten Timestamps**: Re-logging in from a device updates `last_active_at` in place, destroying historical access records.
5. **Lack of Correlation Identifiers**: Requests lack unified `request_id` or `trace_id` metadata bridging API requests to security events.

### 3.2 Security Risks

| Risk Area | Description | Severity | Potential Impact |
|---|---|---|---|
| **Incident Unawareness** | Compromised accounts or token theft cannot be investigated after session revocation due to lack of historical audit logs. | **HIGH** | Incapacity to perform post-incident forensics. |
| **Silent Credential Stuffing** | Rapid failed OTP requests or login attempts leave no structured failure event records in DB. | **HIGH** | Inability to trigger IP bans or account lockouts. |
| **Token Reuse Ephemerality** | Refresh token reuse revokes the session, but security teams have no audit table to review compromised tokens. | **HIGH** | Undetected persistent attacker presence. |
| **Operational Log Loss** | Application console logs (`stdout`/logback) are ephemeral and lost upon container restarts or log rotation. | **MEDIUM** | Compliance failure (OWASP A09:2021). |

---

## 4. Recommended Architecture

We recommend a **Dual-Tier Event System** separating end-user activity visibility from administrative/security audit requirements.

```
                                  ┌────────────────────────┐
                                  │ Authentication / Device│
                                  │       Operation        │
                                  └───────────┬────────────┘
                                              │
                                   Publishes Event (Async)
                                              │
                                  ┌───────────▼────────────┐
                                  │   Security Event Bus   │
                                  └─────┬────────────┬─────┘
                                        │            │
                      ┌─────────────────┘            └─────────────────┐
                      ▼                                                ▼
         ┌──────────────────────────┐                    ┌──────────────────────────┐
         │   User Login History     │                    │  Security Audit Event    │
         │   (User-Facing Log)      │                    │     (Admin / SIEM)       │
         ├──────────────────────────┤                    ├──────────────────────────┤
         │ • User Account Activity  │                    │ • Full Security Audit    │
         │ • Masked IP / Metadata   │                    │ • Failed Logins & Attacks│
         │ • Filtered & Friendly    │                    │ • Token Reuse & IDORs    │
         │ • Retention: 90 Days     │                    │ • Retention: 365 Days    │
         └──────────────────────────┘                    └──────────────────────────┘
```

### 4.1 Tier A: User-Facing Login History (`user_login_history`)
- **Purpose**: Displays user account activity in the iOS app / Web profile ("Recent Device Activity & Logins").
- **Characteristics**: Filtered, readable, user-understandable, privacy-masked (coarse IP/Location).
- **Retention**: 90 Days.

### 4.2 Tier B: Security Event Audit Log (`security_audit_events`)
- **Purpose**: Comprehensive security, compliance, threat detection, and forensic audit trail for administrators.
- **Characteristics**: Immutable, append-only, high-granularity, captures both SUCCESS and FAILURE, contains security reason codes.
- **Retention**: 365 Days (1 Year+).

---

## 5. Event Taxonomy Design

Standardized dot-notation event classification:

```
[CATEGORY]_[SUBCATEGORY]_[OUTCOME]
```

### Proposed Event Vocabulary:

#### 1. Authentication Events (`AUTHN`)
- `AUTHN_OTP_REQUESTED` — OTP code requested by user/client.
- `AUTHN_OTP_DELIVERED` — OTP successfully dispatched via SMS/Email.
- `AUTHN_OTP_FAILED` — Incorrect OTP code entered.
- `AUTHN_OTP_EXPIRED` — Attempted verification of expired OTP.
- `AUTHN_LOGIN_SUCCESS` — Successful user authentication & session creation.
- `AUTHN_LOGIN_FAILED` — Login failed due to account status or eligibility.

#### 2. Session Lifecycle Events (`SESSION`)
- `SESSION_CREATED` — New active session established.
- `SESSION_REFRESH_SUCCESS` — Access/Refresh tokens rotated successfully.
- `SESSION_REFRESH_FAILED` — Token refresh attempt rejected (expired/invalid).
- `SESSION_LOGOUT` — Explicit user logout for current session.
- `SESSION_LOGOUT_ALL` — Bulk revocation of all user sessions.
- `SESSION_EXPIRED` — Session reached TTL expiration.
- `SESSION_REVOKED` — Administrative or automated session revocation.

#### 3. Device Security Events (`DEVICE`)
- `DEVICE_REGISTERED` — New device registered to user account.
- `DEVICE_REACTIVATED` — Previously revoked device reactivated on re-login.
- `DEVICE_REVOKED` — Device explicitly revoked by user or admin.
- `DEVICE_REPLACED` — Device swapped via `replaceDeviceId`.
- `DEVICE_LIMIT_REACHED` — Login attempt blocked due to 2-device maximum.

#### 4. Authorization & Security Threat Events (`SECURITY` / `AUTHZ`)
- `AUTHZ_DENIED` — Permission check failed for protected endpoint.
- `SECURITY_TOKEN_REUSE` — **CRITICAL**: Stolen/reused refresh token detected.
- `SECURITY_INVALID_TOKEN` — Malformed or invalid JWT signature encountered.
- `SECURITY_IDOR_ATTEMPT` — Unauthorized resource access attempt blocked.
- `SECURITY_SUSPICIOUS_ACTIVITY` — High-frequency requests or rate limit breached.

---

## 6. Event Attribute Schema & OWASP Privacy Policy

### 6.1 Event Attribute Definitions

| Field Name | Data Type | Classification | Description / Policy |
|---|---|---|---|
| `id` | BIGINT | **REQUIRED** | Primary Key. |
| `event_id` | UUID | **REQUIRED** | Global unique event identifier. |
| `user_id` | BIGINT | **REQUIRED** | User ID associated with event (NULL for unauthenticated failures). |
| `event_type` | VARCHAR(100) | **REQUIRED** | Standardized taxonomy enum. |
| `outcome` | VARCHAR(20) | **REQUIRED** | `SUCCESS`, `FAILURE`, `BLOCKED`. |
| `reason_code` | VARCHAR(100) | **OPTIONAL** | Error/security code (e.g. `TOKEN_REUSE_DETECTED`, `MAX_DEVICES_EXCEEDED`). |
| `device_id` | BIGINT | **OPTIONAL** | Foreign key to `devices.id` (if applicable). |
| `device_identifier` | VARCHAR(255) | **REQUIRED** | Client installation UUID. |
| `session_id` | BIGINT | **OPTIONAL** | Foreign key to `auth_sessions.id`. |
| `platform` | VARCHAR(50) | **REQUIRED** | `IOS`, `ANDROID`, `WEB`. |
| `app_version` | VARCHAR(50) | **OPTIONAL** | Client app version string. |
| `ip_address` | VARCHAR(45) | **RESTRICTED** | IPv4/IPv6 address. Masked for user-facing API; raw for Security Audit. |
| `user_agent` | VARCHAR(500) | **OPTIONAL** | Sanitized raw user agent. |
| `created_at` | TIMESTAMPTZ | **REQUIRED** | Timestamp of event occurrence. |

### 6.2 OWASP PII & Sensitive Data Protection Policy

> [!CAUTION]
> **STRICT PROHIBITION ON SENSITIVE DATA**: Under no circumstances shall secrets, credentials, or raw tokens be stored in log files or audit tables.

- **NEVER LOG**: Plaintext Passwords, OTP Codes, Access Tokens, Refresh Tokens, Refresh Token Hashes, JWT Signatures, Private Keys.
- **HASH / MASK**:
  - **IP Addresses**: User-facing API returns anonymized IP (e.g., `192.168.x.x` or `2001:db8::xxxx`). Security Audit DB stores raw IP strictly restricted to security personnel.
  - **User-Agent**: User-facing UI receives parsed metadata (`iOS 17.4, iPhone 15 Pro`); Security Audit DB stores raw User-Agent string.

---

## 7. Operational Resilience & Asynchronous Event Architecture

### 7.1 Failure of Logging Policy
**Core Requirement**: A failure in security event persistence MUST NOT cause an account-wide denial of service or break primary business operations (login, playback, device replacement).

- **Authentication & Core Operations**: Execute business logic synchronously.
- **Event Audit Dispatch**: Decoupled asynchronously using Spring `ApplicationEventPublisher` + `@Async` event listeners or `@TransactionalEventListener(phase = AFTER_COMMIT)`.
- **Fallback**: If DB event insertion fails, fallback to structured JSON stderr/stdout logger (`SECURITY_AUDIT_FALLBACK_LOGGER`) so SIEM log collectors (Datadog, Elastic) capture the event independently of PostgreSQL.

### 7.2 Transaction Boundaries
- **Transactional Consistency**: Security events for successful operations (e.g. `DEVICE_REPLACED`) MUST NOT be emitted if the underlying database transaction rolls back.
- **Solution**: Use `@TransactionalEventListener(phase = AFTER_COMMIT)` for success events, ensuring audit logs reflect actual state changes.
- **Failure Events**: Emitted immediately via `@EventListener` outside the main business transaction so failed attempts are recorded even when business transactions abort.

---

## 8. Retention & Tamper Protection Policies

### 8.1 Retention Periods

```
┌────────────────────────────────────────────────────────────────────────┐
│                        RETENTION TIERING POLICY                        │
├──────────────────────────┬───────────────────┬─────────────────────────┤
│ Tier                     │ Retention Period  │ Action on Expiry        │
├──────────────────────────┼───────────────────┼─────────────────────────┤
│ User Login History       │ 90 Days           │ Automated Daily Purge   │
│ Security Audit Log       │ 365 Days (1 Year) │ Archive to Cold Storage │
│ Application Console Logs │ 14 Days           │ Logrotate Purge         │
└──────────────────────────┴───────────────────┴─────────────────────────┘
```

### 8.2 Tamper Protection Strategy
1. **Append-Only Database Tables**: Database roles used by application services granted `INSERT` and `SELECT` privileges only on `security_audit_events`. `UPDATE` and `DELETE` explicitly REVOKED.
2. **Dedicated DB User for Purging**: Automated cleanup/archival jobs use a restricted maintenance service role executing scheduled partitioning/purges.

---

## 9. Proposed API Specifications (Design Only)

### 9.1 User-Facing Login History Endpoint (Design)

```
GET /api/v1/account/login-history?page=0&size=20
```

- **Headers**: `Authorization: Bearer <access_token>`
- **Response Format**: `HTTP 200 OK`

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 1042,
        "eventType": "LOGIN_SUCCESS",
        "displayName": "LOGIN_SUCCESS",
        "deviceName": "Hardik's iPhone",
        "platform": "IOS",
        "appVersion": "1.4.0",
        "maskedIpAddress": "172.56.xxx.xxx",
        "isCurrentSession": true,
        "eventTime": "2026-08-21T21:40:00Z"
      },
      {
        "id": 988,
        "eventType": "DEVICE_REPLACED",
        "displayName": "DEVICE_REPLACED",
        "deviceName": "MacBook Web",
        "platform": "WEB",
        "appVersion": "1.4.0",
        "maskedIpAddress": "172.56.xxx.xxx",
        "isCurrentSession": false,
        "eventTime": "2026-08-20T14:15:30Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 2,
    "totalPages": 1
  },
  "message": "Login history retrieved successfully",
  "timestamp": "2026-08-21T22:20:00Z"
}
```

### 9.2 Admin Security Events Endpoint (Design)

```
GET /api/v1/admin/security/events?userId=32431&eventType=SECURITY_TOKEN_REUSE&page=0&size=50
```

- **Headers**: `Authorization: Bearer <access_token>`
- **Required Permission**: `SECURITY_AUDIT_VIEW`
- **Response**: Full security audit payload including raw IP, reason code, and technical metadata.

---

## 10. Industry Benchmarks & OWASP Alignment

### 10.1 Commercial OTT Platform Benchmark

| Capability | Netflix | Disney+ | Prime Video | CommunityOTT (Proposed Phase C.2) |
|---|---|---|---|---|
| **Recent Device Activity** | Yes ("Recent device streaming activity") | Yes | Yes ("Your Devices") | **Yes** (`GET /api/v1/account/login-history`) |
| **Sign Out All Devices** | Yes | Yes | Yes | **Yes** (`POST /api/v1/auth/logout-all`) |
| **Individual Device Revoke** | Yes | Partial | Yes | **Yes** (`POST /api/v1/devices/{id}/revoke`) |
| **Security Alert on New Login** | Email Notification | Email Code | Email Code | **Proposed Notification Bus Integration** |
| **Security Alert on Token Reuse** | Internal SIEM | Internal SIEM | Internal SIEM | **Yes** (`SECURITY_TOKEN_REUSE` Audit Log) |

### 10.2 OWASP ASVS v4.0 & Top 10 Alignment

- **OWASP Top 10 (A09:2021 — Security Logging & Monitoring Failures)**:
  - Addresses failure to log login failures, token reuse, and authorization denials.
  - Ensures audit logs are stored securely with tamper protection.
- **OWASP ASVS v4.0 (V7.1 — Log Content Requirements)**:
  - Ensures logged events contain timestamp, event type, outcome, user identity, and source IP.
  - Guarantees zero logging of sensitive authentication credentials or tokens (ASVS 7.1.1).

---

## 11. Current Implementation Evaluation & Scorecard

| Evaluation Area | Current Score | Notes & Justification |
|---|---|---|
| **Login History** | **3 / 10** | Active sessions exist, but historical logins are overwritten/lost on device re-login. |
| **Authentication Event Logging** | **4 / 10** | Ephemeral application logs exist; no persistent audit table for successes or failures. |
| **Session Auditability** | **6 / 10** | `auth_sessions` tracks current state well, but lacks historical state transition log. |
| **Device Security Audit** | **6 / 10** | `devices` tracks current registered hardware, but metadata updates overwrite access history. |
| **Authorization Audit** | **2 / 10** | RBAC denials logged at trace level only; IDOR attempts unrecorded. |
| **Privacy & PII** | **7 / 10** | Passwords/tokens not logged, but IP and User-Agent policies lack structured masking rules. |
| **Security Monitoring Readiness**| **3 / 10** | Token reuse triggers logger alert, but cannot be queried via API or SIEM integration. |
| **Audit Integrity** | **2 / 10** | No append-only audit tables exist yet. |
| **User Security Visibility** | **1 / 10** | No end-user login history or account security visibility endpoints implemented yet. |
| **OTT Benchmark Alignment** | **5 / 10** | Device management is strong (Phase B.2), but user activity transparency is missing. |

### **OVERALL LOGIN & SECURITY AUDIT SCORE**: `3.9 / 10`

---

## 12. Gap Matrix

| Area | Current State | Risk | Required Change | Priority |
|---|---|---|---|---|
| **Audit Storage** | Ephemeral app logs (`log.info`/`log.warn`). Operational tables updated in place. | High (Loss of security audit trail) | Create Flyway migration `V21` for `security_audit_events` and `user_login_history`. | **P0** |
| **Failed Auth Logging** | OTP failures and invalid logins not recorded in DB. | High (Credential stuffing undetected) | Publish `AUTHN_OTP_FAILED` and `AUTHN_LOGIN_FAILED` audit events. | **P0** |
| **Token Reuse Alerting** | Refresh token reuse revokes session but stores no audit record. | High (Unobserved account takeover) | Persist `SECURITY_TOKEN_REUSE` audit event with full request context. | **P0** |
| **User Login History API** | No endpoint for users to view device login history. | Medium (Lack of user security visibility) | Implement `GET /api/v1/account/login-history` with pagination & IP masking. | **P1** |
| **RBAC / IDOR Audit** | Permission denials logged at trace level; IDOR attempts unrecorded. | Medium (Undetected privilege escalation) | Capture `AUTHZ_DENIED` and `SECURITY_IDOR_ATTEMPT` events in audit listener. | **P1** |
| **Asynchronous Logging** | Logging code directly coupled to operations. | Medium (Logging failure could block auth) | Implement Spring `@Async` / `@TransactionalEventListener` event pipeline. | **P1** |
| **RBAC Security Permission** | No dedicated permission for security log access. | Medium (Over-privileged admin access) | Create `SECURITY_AUDIT_VIEW` permission for security audit endpoints. | **P2** |

---

## 13. Final Recommendations & Implementation Roadmap

### 13.1 Strategic Architecture Decision
We recommend splitting future development into **TWO DISTINCT MODULES**:
1. **Module C.2**: Security Event Audit Infrastructure (`security_audit_events`, async event pipeline, security incident logging).
2. **Module C.3**: User-Facing Account Security & Login History (`user_login_history`, `GET /api/v1/account/login-history` API, iOS security history view).

### 13.2 Recommended Next Phase
Proceed immediately to **PHASE C.2 — SECURITY EVENT AUDIT INFRASTRUCTURE IMPLEMENTATION**.

Establishing the persistent `security_audit_events` schema and asynchronous event bus first ensures that all subsequent feature modules automatically inherit production-grade security auditing and SIEM readiness.

---

## 14. Verification & Git Safety Status

- **Code Modifications Executed**: 0
- **Database Migrations Created**: 0
- **Git Operations**: 0 commits, 0 pushes, 0 resets, 0 cleans.
- **Documentation**: Saved exclusively to [`Docs/COMMUNITYOTT_LOGIN_SECURITY_AUDIT.md`](file:///Users/hardik/Documents/IOS/CommunityOTT/Docs/COMMUNITYOTT_LOGIN_SECURITY_AUDIT.md).
