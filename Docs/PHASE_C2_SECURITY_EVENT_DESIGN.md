# Phase C.2 — Security Event Audit Architecture Design Specification

**Project Name**: CommunityOTT Backend  
**Module**: Security Event Audit Infrastructure (Phase C.2)  
**Status**: DESIGN APPROVED FOR IMPLEMENTATION  
**Source Document**: [`Docs/COMMUNITYOTT_LOGIN_SECURITY_AUDIT.md`](file:///Users/hardik/Documents/IOS/CommunityOTT/Docs/COMMUNITYOTT_LOGIN_SECURITY_AUDIT.md)  

---

## 1. Executive Summary & Objective

Phase C.2 establishes a production-grade, immutable, asynchronous **Security Event Audit Infrastructure** (`security_audit_events`) for CommunityOTT.

### Key Architectural Goals:
- **Immutable Security Audit Trail**: Captures authentication lifecycle events, OTP failures, login failures, session state transitions, token reuse attempts, device registrations/revocations, RBAC denials, and IDOR attempts.
- **Asynchronous & Non-Blocking**: Event dispatch is decoupled from business logic. Business operations (login, streaming, device replacement) will NEVER fail if audit persistence encounters an error.
- **Strict Transactional Alignment**: Successful business operations publish events via `@TransactionalEventListener(phase = AFTER_COMMIT)`. Failure events (e.g. invalid OTP, token theft, 401/403) publish asynchronously without transactional dependency.
- **OWASP Compliance**: Zero logging of secrets (passwords, OTPs, access/refresh tokens, token hashes, or secret keys).
- **SIEM & Monitoring Readiness**: Captures trace correlation IDs (`request_id`, `trace_id`) and structured metadata to support automated threat detection.

---

## 2. High-Level Architecture & Component Flow

```
========================================================================================================
                                     BUSINESS OPERATION LAYER
========================================================================================================
 AuthenticationService / DeviceService / JwtAuthenticationFilter / RbacAuthorizationService
                                                │
                                                ▼
                             Constructs & Publishes SecurityAuditEvent
                                                │
                                                ▼
                                    ApplicationEventPublisher
                                                │
             ┌──────────────────────────────────┴──────────────────────────────────┐
             │                                                                    │
   [SUCCESSFUL TRANSACTIONS]                                             [FAILED / NON-TRANSACTIONAL]
             │                                                                    │
             ▼                                                                    ▼
@TransactionalEventListener                                                 @EventListener
   (phase = AFTER_COMMIT)                                                         │
             │                                                                    │
             └──────────────────────────────────┬─────────────────────────────────┘
                                                │
                                                ▼
                                Async Security Audit Handler
                              (@Async("securityAuditExecutor"))
                                                │
                                                ├──────────────┐ (On Failure)
                                                ▼              ▼
                                     SecurityAuditRepository  Fallback Logger
                                                │             (SECURITY_AUDIT_FALLBACK_LOGGER)
                                                ▼             (Structured JSON to stderr)
                                      security_audit_events
                                                │
                                                ▼
                                    Future Admin SIEM / Monitoring
========================================================================================================
```

### Architectural Principles:
1. **Publisher-Subscriber Decoupling**: Business services publish strongly-typed Spring domain events (`SecurityAuditEvent`).
2. **Transaction Safety**: Success events are deferred until database commit completes cleanly. If a business transaction rolls back, no success audit event is written.
3. **Execution Isolation**: The listener runs on a dedicated thread pool (`securityAuditExecutor`). Processing failures are caught internally and written to a structured fallback logger, guaranteeing zero impact on client HTTP responses.

---

## 3. Security Audit vs. User Login History Separation

CommunityOTT enforces a strict architectural boundary between **Security Audit Logging** (Phase C.2) and **User-Facing Login History** (Phase C.3):

```
┌───────────────────────────────────────────────┬───────────────────────────────────────────────┐
│ Feature Dimension                             │ Phase C.2 — Security Audit Events             │ Phase C.3 — User Login History                │
├───────────────────────────────────────────────┼───────────────────────────────────────────────┼───────────────────────────────────────────────┤
│ Target Audience                               │ Security Admins, Automated SIEM, Forensics    │ End-User (iOS App / Web Account Settings)     │
│ Table Name                                    │ security_audit_events                         │ user_login_history                            │
│ Data Granularity                              │ Exhaustive (Success, Failure, Security Alerts)│ High-Level (Success Logins, Device Swaps)     │
│ IP Address Visibility                         │ Raw IPv4/IPv6 Address (Restricted Admin)      │ Anonymized / Masked IP (e.g. 172.56.xxx.xxx) │
│ User-Agent Handling                           │ Full Raw User-Agent String                    │ Parsed Friendly Model (e.g. "iPhone 15 Pro") │
│ Event Types Captured                          │ ALL (AUTHN, SESSION, DEVICE, AUTHZ, SECURITY) │ User-facing (LOGIN, LOGOUT, DEVICE_ADDED)     │
│ Retention                                     │ 365 Days (1 Year)                             │ 90 Days                                       │
└───────────────────────────────────────────────┴───────────────────────────────────────────────┴───────────────────────────────────────────────┘
```

---

## 4. Final Event Taxonomy

Events are categorized using the standardized naming format: `[CATEGORY]_[SUBCATEGORY]_[OUTCOME]`

```
                       ┌────────────────────────────────────────┐
                       │          Event Taxonomy Enum           │
                       └───────────────────┬────────────────────┘
                                           │
         ┌──────────────────┬──────────────┴───────┬──────────────────┐
         ▼                  ▼                      ▼                  ▼
    Authentication       Session                 Device          Security & AuthZ
       (AUTHN)          (SESSION)               (DEVICE)        (SECURITY / AUTHZ)
  • OTP_REQUESTED    • CREATED              • REGISTERED       • AUTHZ_DENIED
  • OTP_DELIVERED    • REFRESH_SUCCESS      • REACTIVATED      • TOKEN_REUSE
  • OTP_FAILED       • REFRESH_FAILED       • REVOKED          • INVALID_TOKEN
  • OTP_EXPIRED      • LOGOUT               • REPLACED         • IDOR_ATTEMPT
  • LOGIN_SUCCESS    • LOGOUT_ALL           • LIMIT_REACHED    • SUSPICIOUS_ACTIVITY
  • LOGIN_FAILED     • REVOKED                                 
```

### Event Definitions:
1. `AUTHN_OTP_REQUESTED`: Client requested an OTP code.
2. `AUTHN_OTP_DELIVERED`: OTP successfully dispatched via delivery provider.
3. `AUTHN_OTP_FAILED`: User submitted an incorrect OTP code.
4. `AUTHN_OTP_EXPIRED`: User submitted an expired OTP code.
5. `AUTHN_LOGIN_SUCCESS`: OTP verified; user authenticated and active session established.
6. `AUTHN_LOGIN_FAILED`: Verification rejected due to inactive/blocked user status.
7. `SESSION_CREATED`: Server-side `AuthSession` persisted.
8. `SESSION_REFRESH_SUCCESS`: Access and refresh tokens rotated successfully.
9. `SESSION_REFRESH_FAILED`: Refresh request rejected (expired or malformed token).
10. `SESSION_LOGOUT`: Current session explicitly terminated by user.
11. `SESSION_LOGOUT_ALL`: All user sessions revoked via logout-all endpoint.
12. `SESSION_REVOKED`: Session terminated administratively or via device revocation.
13. `DEVICE_REGISTERED`: New hardware device bound to user account.
14. `DEVICE_REACTIVATED`: Previously revoked device reactivated upon re-login.
15. `DEVICE_REVOKED`: Registered device soft-deleted by user or admin.
16. `DEVICE_REPLACED`: Device swapped atomically via `replaceDeviceId`.
17. `DEVICE_LIMIT_REACHED`: Device registration blocked due to 2-device maximum limit.
18. `AUTHZ_DENIED`: RBAC permission check failed for endpoint.
19. `SECURITY_TOKEN_REUSE`: Stolen/reused refresh token detected.
20. `SECURITY_INVALID_TOKEN`: Malformed JWT signature or invalid claims.
21. `SECURITY_IDOR_ATTEMPT`: Unauthorized cross-user resource access attempt blocked.
22. `SECURITY_SUSPICIOUS_ACTIVITY`: Rate limit breached or anomalous access pattern detected.

---

## 5. Event Outcomes

Every event MUST be classified under exactly one outcome state:

| Outcome | Definition | Typical Event Examples |
|---|---|---|
| `SUCCESS` | Operation completed cleanly without error. | `AUTHN_LOGIN_SUCCESS`, `SESSION_REFRESH_SUCCESS`, `DEVICE_REGISTERED`, `DEVICE_REPLACED` |
| `FAILURE` | Operation attempted but failed due to invalid user input or operational error. | `AUTHN_OTP_FAILED`, `AUTHN_OTP_EXPIRED`, `AUTHN_LOGIN_FAILED`, `SESSION_REFRESH_FAILED` |
| `BLOCKED` | Operation rejected by security controls, rate limits, or authorization policies. | `DEVICE_LIMIT_REACHED`, `SECURITY_TOKEN_REUSE`, `SECURITY_IDOR_ATTEMPT`, `AUTHZ_DENIED` |

---

## 6. Comprehensive Data Model & Schema Design

### 6.1 Database Migration Specification (`V21__create_security_audit_events.sql`)

```sql
-- Flyway Migration: V21__create_security_audit_events.sql
-- Module: Security Event Audit Infrastructure (Phase C.2)

CREATE TABLE security_audit_events (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL,
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    event_type VARCHAR(100) NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    reason_code VARCHAR(100),
    device_id BIGINT REFERENCES devices(id) ON DELETE SET NULL,
    device_identifier VARCHAR(255) NOT NULL,
    session_id BIGINT REFERENCES auth_sessions(id) ON DELETE SET NULL,
    platform VARCHAR(50) NOT NULL,
    app_version VARCHAR(50),
    ip_address VARCHAR(45) NOT NULL,
    user_agent VARCHAR(500),
    request_id VARCHAR(100),
    trace_id VARCHAR(100),
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_security_events_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE', 'BLOCKED'))
);

-- Performance & Indexing Strategy
CREATE INDEX idx_security_events_user_time ON security_audit_events(user_id, created_at DESC);
CREATE INDEX idx_security_events_type_time ON security_audit_events(event_type, created_at DESC);
CREATE INDEX idx_security_events_device_time ON security_audit_events(device_id, created_at DESC);
CREATE INDEX idx_security_events_session_time ON security_audit_events(session_id, created_at DESC);
CREATE INDEX idx_security_events_ip_time ON security_audit_events(ip_address, created_at DESC);
CREATE INDEX idx_security_events_request_id ON security_audit_events(request_id);
```

---

## 7. Field Classification & Technical Rationale

| Field Name | Classification | Rationale & Policy |
|---|---|---|
| `id` | **REQUIRED** | Surrogate primary key for relational indexing. |
| `event_id` | **REQUIRED** | Globally unique UUID generated in Java prior to event publication. |
| `user_id` | **OPTIONAL** | Nullable. Set to user ID when known; NULL for unauthenticated failures. |
| `event_type` | **REQUIRED** | Strongly typed string enum matching taxonomy. |
| `outcome` | **REQUIRED** | State enum (`SUCCESS`, `FAILURE`, `BLOCKED`). |
| `reason_code` | **OPTIONAL** | Error/security code (e.g. `TOKEN_REUSE_DETECTED`, `MAX_DEVICES_EXCEEDED`). |
| `device_id` | **OPTIONAL** | Foreign key to `devices.id`. Soft reference (`ON DELETE SET NULL`). |
| `device_identifier` | **REQUIRED** | Client installation UUID. Preserved independently for forensics if device entity is deleted. |
| `session_id` | **OPTIONAL** | Foreign key to `auth_sessions.id`. Soft reference (`ON DELETE SET NULL`). |
| `platform` | **REQUIRED** | Client platform (`IOS`, `ANDROID`, `WEB`). |
| `app_version` | **OPTIONAL** | Application build version string. |
| `ip_address` | **REQUIRED** | Client IPv4/IPv6 address. Raw storage restricted to security admin view. |
| `user_agent` | **OPTIONAL** | Client HTTP User-Agent header (truncated to 500 chars). |
| `request_id` | **OPTIONAL** | HTTP Request ID generated by API gateway/filter for request tracing. |
| `trace_id` | **OPTIONAL** | Distributed trace ID (W3C / OpenTelemetry). |
| `metadata` | **OPTIONAL** | JSONB payload for non-sensitive contextual Key-Values (max 2KB). |
| `created_at` | **REQUIRED** | Microsecond-precision timestamp of event creation. |

---

## 8. Foreign Key Policy & Audit Trail Preservation

To ensure historical security audit records are NEVER deleted when operational records are purged or soft-deleted:

- `user_id REFERENCES users(id) ON DELETE SET NULL`
- `device_id REFERENCES devices(id) ON DELETE SET NULL`
- `session_id REFERENCES auth_sessions(id) ON DELETE SET NULL`

> [!IMPORTANT]
> **Forensic Preservability**: Even if an operational `AuthSession` or `Device` record is hard-purged, the audit event row remains intact. The scalar `device_identifier`, `ip_address`, `request_id`, and timestamp preserve historical evidence.

---

## 9. OWASP Privacy Policy & Sensitive Data Boundaries

```
┌────────────────────────────────────────────────────────────────────────┐
│                     DATA CLASSIFICATION MATRIX                        │
├─────────────────┬──────────────────────────────────────────────────────┤
│ Classification  │ Field / Attribute                                    │
├─────────────────┼──────────────────────────────────────────────────────┤
│ SAFE            │ event_id, event_type, outcome, reason_code, platform,│
│                 │ app_version, request_id, trace_id, created_at        │
├─────────────────┼──────────────────────────────────────────────────────┤
│ SENSITIVE       │ user_id, device_id, device_identifier, session_id,   │
│ (Restricted)    │ ip_address, user_agent, metadata                     │
├─────────────────┼──────────────────────────────────────────────────────┤
│ FORBIDDEN       │ Plaintext Passwords, OTP Values, Access Tokens,      │
│ (NEVER STORED)  │ Refresh Tokens, Token Hashes, JWT Signatures,        │
│                 │ Encryption Keys, Authorization Headers, Cookies      │
└─────────────────┴──────────────────────────────────────────────────────┘
```

### Metadata Sanitization Rules:
When populating the `metadata JSONB` field, the system enforces automatic key redaction:
- Prohibited key names (case-insensitive regex match): `.*password.*`, `.*otp.*`, `.*token.*`, `.*secret.*`, `.*authorization.*`, `.*cookie.*`.

---

## 10. Request Correlation Infrastructure

1. **Request ID Generation**: `CorrelationIdFilter` assigns a unique `request_id` (UUID) to every incoming HTTP request header `X-Request-ID` and injects it into SLF4J `MDC`.
2. **Trace ID Propagation**: Extracts OpenTelemetry/W3C `traceparent` header if present.
3. **Async MDC Transfer**: `securityAuditExecutor` wraps tasks in an `MDCPropagatingDecorator`, propagating `request_id` and `trace_id` to async worker threads during event processing.

---

## 11. Immutability & Protection Strategy

`security_audit_events` is an **append-only** audit log:

1. **Application Layer**: `SecurityAuditEventRepository` exposes ONLY `save()` and `findBy...()` queries. Interface DOES NOT extend `CrudRepository` methods that allow `delete()` or `deleteAll()`.
2. **Database Privileges**: PostgreSQL application role granted `INSERT` and `SELECT` permissions on `security_audit_events`. `UPDATE` and `DELETE` explicitly REVOKED.

---

## 12. Operational Resilience & Failure Handling

### Core Mandate:
**Audit logging failures MUST NOT cause business operation failures.** If event insertion fails (e.g. database timeout or constraint error), the business transaction (login, playback, device replacement) MUST complete successfully and return `HTTP 200 OK` to the client.

### Failure Fallback Sequence:
```
1. Async Listener attempts SecurityAuditRepository.save(event).
2. IF Exception occurs:
   a. Catch exception cleanly inside try-catch block.
   b. Format event payload as structured JSON.
   c. Write to fallback logger: SECURITY_AUDIT_FALLBACK_LOGGER.error(jsonPayload).
   d. SIEM agent (e.g. Datadog / Vector) ingests fallback JSON from stderr.
   e. Zero impact on client HTTP response.
```

---

## 13. Retention Policy & Maintenance

- **Active Storage Window**: 365 Days (1 Year) in PostgreSQL.
- **Maintenance Purge**: Automated daily background job running off-peak:
  ```sql
  DELETE FROM security_audit_events WHERE created_at < NOW() - INTERVAL '365 days';
  ```
- **Cold Storage Export**: Optional automated export of purged records to encrypted Object Storage (GCS/S3) prior to deletion for compliance archives.

---

## 14. Admin Access Control (Future API Design)

Future admin endpoint for security auditing:

```
GET /api/v1/admin/security/events?userId={id}&eventType={type}&outcome={outcome}&fromDate={ts}&toDate={ts}&page=0&size=50
```

### RBAC Permission Specification:
- Dedicated Permission: `SECURITY_AUDIT_VIEW`
- **Access Matrix**:
  - `SUPER_ADMIN`: ALLOWED
  - `MANAGER`: DENIED by default
  - `CONTENT_MANAGER`: DENIED
  - `USER`: DENIED

---

## 15. Comprehensive Implementation Test Plan (For Phase C.2 Execution)

The future implementation phase MUST create test suite `SecurityAuditEventTest.java` verifying:

1. `test1_LoginSuccessEmitsAuditEvent`: Verifies `AUTHN_LOGIN_SUCCESS` event created with session/device ID.
2. `test2_LoginFailureEmitsAuditEvent`: Verifies `AUTHN_LOGIN_FAILED` event created with `FAILURE` outcome.
3. `test3_OtpFailureEmitsAuditEvent`: Verifies `AUTHN_OTP_FAILED` event emitted with invalid OTP code details.
4. `test4_TokenReuseEmitsSecurityAlertEvent`: Verifies `SECURITY_TOKEN_REUSE` emitted with `BLOCKED` outcome.
5. `test5_DeviceRegistrationEmitsAuditEvent`: Verifies `DEVICE_REGISTERED` event emitted.
6. `test6_DeviceRevocationEmitsAuditEvent`: Verifies `DEVICE_REVOKED` event emitted.
7. `test7_DeviceReplacementEmitsAuditEvent`: Verifies `DEVICE_REPLACED` event emitted after transaction commit.
8. `test8_MaxDevicesReachedEmitsBlockedEvent`: Verifies `DEVICE_LIMIT_REACHED` emitted when limit hit.
9. `test9_RbacDenialEmitsAuthzDeniedEvent`: Verifies `AUTHZ_DENIED` event emitted on 403.
10. `test10_IdorAttemptEmitsSecurityEvent`: Verifies `SECURITY_IDOR_ATTEMPT` emitted on cross-user access attempt.
11. `test11_RequestAndTraceIdPropagated`: Verifies `request_id` and `trace_id` captured in event.
12. `test12_SecretsRedactedFromMetadata`: Verifies tokens and passwords stripped from event metadata.
13. `test13_NoForbiddenPiiStored`: Verifies zero sensitive credential leakage.
14. `test14_AuditDatabaseFailureDoesNotBlockLogin`: Verifies login succeeds even if audit repository throws exception.
15. `test15_TransactionalRollbackPreventsSuccessEvent`: Verifies failed business transaction does NOT publish `AFTER_COMMIT` success event.
16. `test16_ConcurrentEventLogging`: Verifies high-throughput concurrent events process cleanly without deadlocks.
17. `test17_CrossUserAuditIsolation`: Verifies admin query filtering by `user_id` strictly isolates audit records.
18. `test18_PaginationAndSorting`: Verifies admin audit query supports sorting and pagination.
19. `test19_ImmutabilityEnforced`: Verifies update/delete operations rejected.
20. `test20_RetentionQueryCompatibility`: Verifies date range queries execute efficiently using `idx_security_events_type_time`.

---

## 16. Verification & Implementation Roadmap

### Verification Status:
- **Design Review**: Complete & Approved.
- **Java Source Code Modifications**: 0 lines modified.
- **Database Migrations Created**: 0 files created.
- **Git State**: Clean, 0 commits, 0 pushes.

---

## 17. Final Decision

# **DESIGN APPROVED FOR IMPLEMENTATION**

The Security Event Audit Architecture Design Specification (Phase C.2) is complete, robust, OWASP-compliant, and fully aligned with OTT platform standards.

**Implementation Contract for Phase C.2:**
1. Create Flyway migration `V21__create_security_audit_events.sql`.
2. Implement domain model `SecurityAuditEvent`, `SecurityAuditEventRepository`, and `SecurityAuditEventPublisher`.
3. Implement `@Async` event listener `SecurityAuditEventListener` with fallback logger.
4. Integrate event publishing across `AuthenticationService`, `DeviceService`, `JwtAuthenticationFilter`, `CustomAccessDeniedHandler`, and `GlobalExceptionHandler`.
5. Implement `SecurityAuditEventTest` verifying all 20 test scenarios.
6. Verify full regression (`./mvnw clean test`).

STOP AFTER DESIGN REVIEW.
