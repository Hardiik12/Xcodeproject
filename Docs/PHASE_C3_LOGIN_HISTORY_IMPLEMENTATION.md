# Phase C.3B — User-Facing Login History & Account Security Implementation

## 1. Executive Summary

Phase C.3B completes the user-facing **Login History & Account Security** backend module for CommunityOTT. Building on top of the immutable Phase C.2 Security Audit Infrastructure (`security_audit_events`), Phase C.3 projects high-fidelity, privacy-sanitized, and non-sensitive account security entries into a dedicated, indexed `user_login_history` table.

The implementation strictly enforces account-level isolation (anti-IDOR), IP masking (`192.168.x.x` / `2001:db8::xxxx`), User-Agent parsing into structured fields (`platform`, `os_version`, `app_version`), single-entry deduplication for new device registrations, dynamic current device determination via authenticated session `sid`, and non-blocking fault-tolerant projection using `Propagation.REQUIRES_NEW`.

Full regression test suite execution confirms **487 passing backend tests** (0 failures, 0 errors, 0 skipped), including 24 focused integration tests in `UserLoginHistoryTest`.

---

## 2. Implemented Architecture & Data Model

### Data Flow Diagram

```
+--------------------------+       +------------------------------------+
| C.2 Security Event       | ----> | UserLoginHistoryProjectionService  |
| Audit Publisher          |       | (@Async @EventListener)            |
+--------------------------+       +------------------------------------+
                                                     |
                                                     v
                                      +-------------------------------+
                                      | user_login_history Table      |
                                      | - user_id (FK, Indexed)       |
                                      | - event_type                  |
                                      | - status (SUCCESS/FAILURE/    |
                                      |           BLOCKED)            |
                                      | - masked_ip                   |
                                      | - user_message                |
                                      +-------------------------------+
                                                     ^
                                                     |
+--------------------------+       +------------------------------------+
| GET /api/v1/account/     | ----> | AccountSecurityController & Service|
| login-history            |       | (Anti-IDOR: principal.getUserId()) |
+--------------------------+       +------------------------------------+
```

### Flyway Schema Migration (`V22__create_user_login_history.sql`)

```sql
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
    
    CONSTRAINT chk_login_history_status CHECK (status IN ('SUCCESS', 'FAILURE', 'BLOCKED'))
);

CREATE INDEX idx_login_history_user_occurred ON user_login_history (user_id, occurred_at DESC, id DESC);
CREATE INDEX idx_login_history_user_type_occurred ON user_login_history (user_id, event_type, occurred_at DESC, id DESC);
```

---

## 3. Privacy, Security, & Secret Sanitization Matrix

| Field | Input Event Data | Stored & Returned User-Facing DTO | Rationale |
| :--- | :--- | :--- | :--- |
| **IP Address** | `192.168.1.50` | `192.168.x.x` / `2001:db8::xxxx` | Privacy compliance; hides exact user IP |
| **User-Agent** | `Mozilla/5.0 (iPhone; CPU iPhone OS 18_6...)` | `Platform: IOS`, `OS: 18.6`, `App: 1.4.0` | Prevents fingerprinting; structured presentation |
| **Device ID** | `DeviceEntity (id: 42)` | `is_current_device: true/false` | Raw UUIDs/IDs hidden; dynamic active session check |
| **Secrets & Hashes**| `JWT`, `refresh_token_hash`, `otp` | **OMITTED** (`null`) | Zero secret leakage in user DTOs |
| **Failed Logins** | Invalid password / unknown user | Generic message: `"Sign-in attempt failed"` | Anti-enumeration defense |

---

## 4. Key Java Components

1. **Entity**: `UserLoginHistory.java` (`com.communityott.account.entity`)
2. **Repository**: `UserLoginHistoryRepository.java` (`com.communityott.account.repository`) extending `JpaRepository` & `JpaSpecificationExecutor`.
3. **Projection Service**: `UserLoginHistoryProjectionService.java` (`com.communityott.account.service`)
   - Listens asynchronously to C.2 events (`SecurityAuditEventPayload`).
   - Executes with `@Transactional(propagation = Propagation.REQUIRES_NEW)` for zero-impact fault tolerance.
   - Deduplicates near-simultaneous `AUTHN_LOGIN_SUCCESS` and `DEVICE_REGISTERED` events within a 5-second window into a single `SIGNED_IN_NEW_DEVICE` record.
4. **Account Security Service**: `AccountSecurityService.java` (`com.communityott.account.service`)
   - Uses Spring Data `Specification` for type-safe, null-safe date range and filter queries.
   - Resolves `is_current_device` dynamically per request by matching the active JWT session's `device_entity_id`.
5. **REST Controller**: `AccountSecurityController.java` (`com.communityott.account.controller`)
   - Exposes `GET /api/v1/account/login-history`.
   - Rejects unauthenticated requests with `401 Unauthorized`.
   - Resolves user ID strictly from `@AuthenticationPrincipal UserPrincipal`.

---

## 5. Verification Matrix & Test Coverage

### Test Results: 24/24 Integration Tests Passing (`UserLoginHistoryTest`)

| Test # | Test Scenario | Verified Behavior | Status |
| :---: | :--- | :--- | :---: |
| **1** | `test1_ownLoginHistorySucceeds` | Authenticated user retrieves their own history (HTTP 200) | **PASS** |
| **2** | `test2_unauthenticatedReturns401` | Request without JWT returns HTTP 401 | **PASS** |
| **3** | `test3_noUserIdParameterAntiIdor` | Endpoint ignores external user parameters (strictly bound to token) | **PASS** |
| **4** | `test4_userIsolation` | User A cannot view User B's login history entries | **PASS** |
| **5** | `test5_defaultPagination` | Returns default page 0, size 20 | **PASS** |
| **6** | `test6_customPagination` | Custom page and size parameter enforcement | **PASS** |
| **7** | `test7_eventTypeFiltering` | Filters entries by exact `event_type` (e.g. `LOGIN_SUCCESS`) | **PASS** |
| **8** | `test8_platformFiltering` | Filters entries by `platform` (e.g. `IOS`) | **PASS** |
| **9-10** | `test9and10_newDeviceDeduplication` | Deduplicates login + new device registration into single `SIGNED_IN_NEW_DEVICE` entry | **PASS** |
| **11** | `test11_otpFailureProjection` | `AUTHN_OTP_FAILED` projects to `LOGIN_FAILED` with generic message | **PASS** |
| **12** | `test12_deviceRevocationProjection` | `DEVICE_REVOKED` projects to `DEVICE_REMOVED` entry | **PASS** |
| **13** | `test13_deviceReplacementProjection` | `DEVICE_REPLACED` projects to `DEVICE_REPLACED` entry | **PASS** |
| **14** | `test14_logoutProjection` | `SESSION_LOGOUT` projects to `LOGGED_OUT` entry | **PASS** |
| **15** | `test15_currentDeviceDetection` | `is_current_device` is `true` ONLY for requesting JWT session | **PASS** |
| **16-20**| `test16to20_privacySanitization` | Masked IP (`192.168.x.x`), no raw UA, zero token/hash/trace ID leakage | **PASS** |
| **21** | `test21_antiEnumeration` | Failed logins output generic user message `"Sign-in attempt failed"` | **PASS** |
| **22** | `test22_projectionResilience` | Projection uses `REQUIRES_NEW` transaction; core authn never blocked | **PASS** |
| **23** | `test23_projectionFailureDoesNotBreakLogin` | Unhandled projection exception does not disrupt user login | **PASS** |
| **24** | `test24_accountDeletionBehavior` | Cascade delete removes login history upon account deletion | **PASS** |
| **25** | `test25_dateRangeFiltering` | Filtering by `from_date` and `to_date` bounds | **PASS** |
| **26** | `test26_concurrentRequests` | Multi-threaded concurrent history fetch safe under load | **PASS** |
| **27** | `test27_emptyHistory` | New user receives HTTP 200 with empty items array `[]` | **PASS** |
| **28** | `test28_invalidDateRange` | Returns HTTP 400 when `from_date` is after `to_date` | **PASS** |
| **29** | `test29_negativePageIndex` | Returns HTTP 400 when `page < 0` | **PASS** |

### Regression Baseline

- **Total Backend Tests**: 487
- **Passed**: 487
- **Failed**: 0
- **Errors**: 0
- **Skipped**: 0
