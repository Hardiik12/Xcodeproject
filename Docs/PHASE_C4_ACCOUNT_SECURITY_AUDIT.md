# CommunityOTT Phase C.4 Account Security & Suspicious Activity Audit

## 1. Executive Summary & Security Posture Inventory

### Current Implementation State (Phases A through C.3)

| Security Component | Implementation Details | Verified Posture |
| :--- | :--- | :--- |
| **Authentication & Tokens** | Mobile/Email OTP, JWT Access Tokens (TTL 15m), Refresh Tokens (TTL 30d) with SHA-256 hash storage. Enforces strict single-use refresh token rotation with immediate session revocation upon token reuse detection (`SECURITY_TOKEN_REUSE`). | **STRONG (Phase A)** |
| **Device & Session Management** | Maximum 2 active registered devices per account (`MAX_ACTIVE_DEVICES = 2`). Strict `sid` -> `AuthSession` -> `Device` binding. Active device replacement workflow (`replaceDeviceId`). Session revocation (`logout`, `logout-all`). | **STRONG (Phase B)** |
| **Security Event Infrastructure** | Immutable append-only `security_audit_events` ledger. Asynchronous `@EventListener` with `AFTER_COMMIT` semantics. Correlation tracking (`request_id`, `trace_id`, `sid`). | **STRONG (Phase C.2)** |
| **User Login History** | Privacy-sanitized `user_login_history` table. Masked IP (`192.168.x.x` / `2001:db8::xxxx`), structured device name, platform, OS, and app version. Anti-IDOR (`principal.getUserId()`). Asynchronous `REQUIRES_NEW` projection. | **STRONG (Phase C.3)** |
| **Rate Limiting** | Endpoint-level rate limiting (bucket-based / filter-based) on authn endpoints. | **MODERATE** |
| **Security Alerts & Notifications** | No automated user security notification channel (Email/SMS/Push) currently exists for security events. | **MISSING (Gap)** |
| **Account Recovery** | OTP-based single-channel recovery. No fallback recovery codes or multi-factor step-up authentication. | **BASIC (Gap)** |

---

## 2. Comprehensive Security Threat Model

| Threat Vector | Classification | Current Defense Mechanism | Residual Risk / Gap |
| :--- | :--- | :--- | :--- |
| **1. OTP Brute Force** | **PROTECTED** | 6-digit cryptographic OTP, short expiration (5m), max 3 verification attempts per challenge. | Low risk. |
| **2. OTP Request Flooding** | **PARTIALLY PROTECTED**| Cooldown window (60s) per identifier. | IP-based request flooding needs IP-bucket rate limiting. |
| **3. OTP Enumeration** | **PROTECTED** | Identical generic HTTP responses for valid & invalid numbers/emails. | None. |
| **4. Account Takeover (ATO)** | **PARTIALLY PROTECTED**| Token reuse revokes sessions; 2-device limit blocks stealth logins. | No real-time user notification when a new device signs in. |
| **5. Refresh Token Theft** | **PROTECTED** | Tokens hashed with SHA-256; raw token never stored in database. | Low risk. |
| **6. Refresh Token Replay** | **PROTECTED** | Token rotation invalidates old tokens; reuse revokes all user sessions. | Low risk. |
| **7. Stolen Access Token** | **PROTECTED** | Short 15-minute access token TTL; `sid` bound to active session state. | Low risk. |
| **8. Malicious Device Reg.** | **PROTECTED** | Hard limit of 2 registered devices per account. | Attacker with OTP can trigger device replacement. |
| **9. Device Replacement Abuse** | **PARTIALLY PROTECTED**| Evicts oldest device to register new device. | Replacement executes without step-up OTP or user warning alert. |
| **10. Repeated Failed Logins** | **PROTECTED** | Failed OTPs published to C.2 audit & projected as generic `"Sign-in attempt failed"`. | No automatic lockout/cool-off on 10+ consecutive failures. |
| **11. Suspicious Geo Change** | **NOT PROTECTED** | Approx location derived from IP, but no anomaly scoring across logins. | Attacker in different continent can log in if OTP obtained. |
| **12. Impossible Travel** | **NOT PROTECTED** | Logged in C.2/C.3 timestamps, but speed-over-distance check is not calculated. | No alert generated for 5,000 km jump in 10 minutes. |
| **13. Abnormal Device Behavior**| **PARTIALLY PROTECTED**| Platform & OS version tracked in `user_login_history`. | No alert on sudden OS/browser agent switching. |
| **14. Repeated Authz Failures** | **PROTECTED** | Custom Spring Security handlers catch 401/403 and emit audit payloads. | Low risk. |
| **15. IDOR Attempts** | **PROTECTED** | Account APIs resolve `userId` strictly from authenticated JWT `principal`. | None. |
| **16. Session Hijacking** | **PROTECTED** | Active server-side `AuthSession` validated on every request in `JwtAuthenticationFilter`. | Low risk. |
| **17. API Scraping** | **PARTIALLY PROTECTED**| Database-level pagination (`fetch first 50 rows only`). | General API rate limiting exists but lacks bot detection. |
| **18. Account Deletion Abuse** | **PROTECTED** | Soft-delete / status check blocks deleted accounts instantly. | None. |
| **19. Logout-All Abuse** | **PARTIALLY PROTECTED**| Revokes all active sessions for `principal.getUserId()`. | Attacker with 1 active session can logout legitimate user devices. |
| **20. Social Engineering Recovery**| **NOT PROTECTED** | Single-channel OTP only. No trusted contact or secondary verification. | High risk if SIM swapped. |

---

## 3. Suspicious Login Detection Architecture

### Deterministic Rule Evaluation vs Machine Learning

| Detection Criterion | Rule Type | Evaluation Logic | Recommendation |
| :--- | :--- | :--- | :--- |
| **New Device Login** | Deterministic | `device_id` not found in user's historic `devices` list. | **Enforce Rule (P0)** |
| **New Platform Login** | Deterministic | First login for `(user_id, platform)` combination. | **Enforce Rule (P1)** |
| **Repeated Login Failures** | Deterministic | 5+ `AUTHN_LOGIN_FAILED` / `AUTHN_OTP_FAILED` within 10 minutes. | **Enforce Rule (P0)** |
| **Device Replacement After Login**| Deterministic | `DEVICE_REPLACED` triggered within 60s of `AUTHN_LOGIN_SUCCESS`. | **Enforce Rule (P0)** |
| **Impossible Travel** | Deterministic | Distance between current & previous login IP > 1,000 km with time delta < 1h. | **Enforce Rule (P1)** |
| **Token Reuse Alert** | Deterministic | `SECURITY_TOKEN_REUSE` emitted during token refresh. | **Enforce Rule (P0)** |
| **Unusual Login Time / Behavior**| ML / Statistical | Gaussian probability density of historic login hour. | **DO NOT IMPLEMENT (Overkill for current scale)** |

> [!NOTE]
> **Verdict on ML**: CommunityOTT does **NOT** require machine learning models at current scale. Deterministic rules combined with time-window thresholding provide 99% of protection value with 0% false-positive ML drift risks.

---

## 4. Security Risk Level Classification Matrix

```
   CRITICAL  | Token Reuse Detected, Rapid Device Replacement, Compromised Refresh Token
   HIGH      | Multiple OTP Failures + New Device, Impossible Travel Alert
   MEDIUM    | New Unrecognized Device Registration, Login from New Platform
   LOW       | Standard Login from Existing Registered Device
```

### Trigger Matrix & Actions

- **LOW**: Event logged to `user_login_history`. Standard session issued.
- **MEDIUM**: Event logged; user-facing notification alert dispatched ("New sign-in on iOS").
- **HIGH**: Event logged; high-priority security email/SMS sent; optional step-up OTP challenge on sensitive account settings.
- **CRITICAL**: Immediate session revocation (`logout-all`); device registration blocked; mandatory account re-verification via SMS/email OTP; real-time security alert dispatched.

---

## 5. Security Event Correlation & Incident Model

### Scenario: Multi-Stage Attack Pattern

```
[1. 5x Failed OTPs] ---> [2. Successful OTP Verification] ---> [3. New Device Registered] ---> [4. Device B Replaced] ---> [5. Token Reuse Attempt]
    (IP 203.0.113.5)              (IP 203.0.113.5)                 (Device ID 99)                 (Device ID 99)                (Revoked Token)
```

### Proposed `security_incidents` Model (Design Only)

To prevent security teams from investigating 10 isolated audit events during an attack, correlate events sharing the same `(user_id, time_window_15m)` or `correlation_id` into a single `SecurityIncident`:

```sql
-- Conceptual Model: security_incidents (DO NOT CREATE IN C.4)
CREATE TABLE security_incidents (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    incident_type   VARCHAR(64) NOT NULL, -- e.g. 'ACCOUNT_TAKEOVER_SUSPECTED'
    severity        VARCHAR(32) NOT NULL, -- LOW, MEDIUM, HIGH, CRITICAL
    status          VARCHAR(32) NOT NULL, -- OPEN, INVESTIGATING, RESOLVED, AUTO_MITIGATED
    first_event_at  TIMESTAMPTZ NOT NULL,
    last_event_at   TIMESTAMPTZ NOT NULL,
    event_count     INT NOT NULL DEFAULT 1,
    risk_score      INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

---

## 6. Security Alerts Specification

| Alert Type | Trigger Condition | Severity | User Visibility | Admin Visibility | Notification Channel | Sanitized User Message |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`ALERT_NEW_DEVICE`** | First login from newly registered `Device` | **MEDIUM** | In-App + Email | Audit Log | Email, Push | *"New sign-in to your CommunityOTT account on iPhone 15."* |
| **`ALERT_DEVICE_REPLACED`** | `replaceDeviceId` executed | **HIGH** | In-App + Email | Security Dashboard | Email, SMS, Push | *"Device 'Living Room TV' was replaced by 'Chrome Web Browser'."* |
| **`ALERT_SUSPICIOUS_LOGIN`**| Impossible travel or 5+ failures before success | **HIGH** | In-App + Email | Security Dashboard | Email, SMS | *"Unusual sign-in activity detected on your account."* |
| **`ALERT_TOKEN_REUSE`** | Refreshed token reused by revoked client | **CRITICAL** | In-App + Email | High-Priority Alert | Email, SMS, Push | *"We detected a security concern and signed out all your devices for your protection."* |

---

## 7. User Experience & Microcopy Design

### Account -> Security Screen Layout

```
+-------------------------------------------------------------------+
|  ACCOUNT SECURITY                                                |
+-------------------------------------------------------------------+
|                                                                   |
|  [!] Security Alert                                               |
|      Unusual sign-in from Hyderabad, India • Today at 10:42 PM    |
|      [Was this you?]   [Secure Account]                           |
|                                                                   |
|  ACTIVE DEVICES (2 of 2)                                         |
|  • iPhone 15 Pro (This Device) - Active now                       |
|  • Samsung Smart TV - Last active 2 hours ago                     |
|                                                                   |
|  RECENT ACTIVITY                                                  |
|  • Signed in on new device (iPhone 15 Pro)   • Today at 10:42 PM   |
|  • Signed out (Chrome Web)                   • Yesterday          |
|                                                                   |
|  [ Sign Out Of All Devices ]                                      |
+-------------------------------------------------------------------+
```

### Microcopy Guidelines
- Never display technical enums (`SECURITY_TOKEN_REUSE`, `AUTHN_LOGIN_FAILED`).
- Use clear, non-alarmist, empowering language:
  - *Internal*: `SECURITY_TOKEN_REUSE` -> *User*: `"We detected an unusual sign-in attempt and signed out all devices to keep your account safe."`
  - *Internal*: `DEVICE_LIMIT_REACHED` -> *User*: `"You've reached your 2-device limit. Choose a device to replace to continue."`

---

## 8. Device Replacement Security & Friction Balancing

### The Max-2-Device Security Dilemma

```
User has 2 Active Devices (Phone + TV)
                   │
                   ▼
       New Laptop tries to sign in
                   │
                   ▼
┌─────────────────────────────────────────────────────────────┐
│  DEVICE LIMIT REACHED (2/2 Active Devices)                   │
│                                                             │
│  To register this Laptop, select a device to sign out:      │
│  [ ] iPhone 15 Pro (Active now)                             │
│  [ ] Living Room Smart TV (Last active 3 days ago)          │
│                                                             │
│  [ Send Verification Code to Confirm ]                      │
└─────────────────────────────────────────────────────────────┘
```

### Recommended Safeguards for Device Replacement
1. **Require Re-Authentication / OTP Confirmation**: When replacing a device, send a 6-digit verification code to the registered email/phone before evicting the existing device.
2. **Post-Replacement Notification**: Immediately notify all remaining active devices via push notification & email when a device replacement occurs.
3. **Replacement Cooldown**: Limit device replacements to maximum 3 replacements per 24 hours per account to prevent automated device-churn attacks.

---

## 9. Account Recovery Architecture

```
                               ┌─────────────────────────┐
                               │ User Loses Device A & B │
                               └────────────┬────────────┘
                                            │
                                            ▼
                               ┌─────────────────────────┐
                               │ Prompt Email/Phone OTP  │
                               └────────────┬────────────┘
                                            │
                                            ▼
                   ┌────────────────────────┴────────────────────────┐
                   │                                                 │
                   ▼                                                 ▼
     [ Access to Phone/Email ]                          [ Lost Access to Email/Phone ]
                   │                                                 │
                   ▼                                                 ▼
        OTP Verified Successfully                        Submit Account Recovery Ticket
                   │                                     (Secondary identity validation)
                   ▼                                                 │
      Existing Devices Revoked;                                      ▼
       New Device Registered                               Admin Manual Audit

```

---

## 10. Industry Benchmark Comparison (OTT Security)

| Security Feature | Netflix | Disney+ | Prime Video | CommunityOTT (Target Post-C.4) |
| :--- | :--- | :--- | :--- | :--- |
| **Max Concurrent Devices** | Tiered (1, 2, 4) | 4 Devices | 3 Devices | **Strict 2 Registered Devices** |
| **Recent Activity Log** | Streaming activity + IP | Device list only | Registered devices | **Privacy-Sanitized Login History** |
| **Sign Out All Devices** | Yes (up to 8h delay) | Yes (immediate) | Yes (immediate) | **Yes (Immediate Server Revocation)** |
| **New Device Alert** | Email on new device | Email on new login | Email on new sign-in | **Proposed Email + In-App Push** |
| **Token Reuse Defense** | Proprietary | Proprietary | OAuth 2.0 / Revocation | **SHA-256 Rotation + Auto-Revoke** |

---

## 11. Final Security Gap Matrix & Actionable Roadmap

| Gap ID | Feature Area | Current State | Risk Level | Priority | Recommended Action |
| :---: | :--- | :--- | :---: | :---: | :--- |
| **GAP-01** | Security Alerts | No automated notification channel | **HIGH** | **P0** | Implement `SecurityAlertService` with Email & In-App notification triggers. |
| **GAP-02** | Device Replacement Safeguard | Instant replacement without step-up | **HIGH** | **P0** | Require OTP confirmation for device replacement; rate limit replacements (max 3/day). |
| **GAP-03** | Impossible Travel Detection | IP location logged, not evaluated | **MEDIUM** | **P1** | Add velocity check between consecutive login events. |
| **GAP-04** | Security Status API | No unified endpoint for security health | **MEDIUM** | **P1** | Expose `GET /api/v1/account/security/status` DTO. |
| **GAP-05** | Rate Limiting Granularity | Basic endpoint rate limits | **MEDIUM** | **P1** | Add IP-based bucket rate limiting for `/otp/request` and `/otp/verify`. |
| **GAP-06** | Admin Security View | Audit events logged, no admin UI | **LOW** | **P2** | Expose read-only security audit query endpoints for `SUPER_ADMIN`. |

---

## 12. Recommended Phase Roadmap

1. **Phase C.4 (Current)**: Account Security & Suspicious Activity Audit (**COMPLETED**).
2. **Phase C.5 (Next Recommended Module)**: **Security Alerts & Automated User Notifications** (Email/In-App alert triggers for new devices, token reuse, and device replacement).
3. **Phase C.6**: **Enhanced Device Replacement & Step-Up Verification** (OTP confirmation during device replacement, cooldown enforcement).
4. **Phase C.7**: **Security Incident Correlation & Admin Security Dashboard**.

---

## 13. Final Decision

```
===================================================================
                  AUDIT COMPLETE — DESIGN REQUIRED
===================================================================
```

**Next Recommended Module for Design & Implementation**: **Phase C.5 — Security Alerts & Automated User Notifications**.
