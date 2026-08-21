# CommunityOTT Authentication & Account OTT Audit

## 1. Executive Summary

This comprehensive audit evaluates the existing **CommunityOTT** backend and iOS frontend implementations for Authentication, Account Security, Role-Based Access Control (RBAC), Device Management, Session Management, Login History, Profiles, and Kids/Parental Controls.

The system features a passwordless OTP-based authentication architecture, short-lived JWT access tokens, Spring Security 6 stateless enforcement, fine-grained RBAC with 31 system permissions, and multi-profile viewing support. However, several production-grade OTT capabilities—including server-side session revocation enforcement, refresh token rotation, explicit device registration limits, persistent login history, and Kids/parental controls—remain missing or partially implemented.

---

## 2. Existing Architecture

### Backend Stack
- **Framework**: Java 21, Spring Boot 3.3.2, Spring Security 6
- **Database**: PostgreSQL with Flyway migrations (V1–V18)
- **Token Format**: JJWT (HMAC-SHA256 signed access tokens, default TTL: 15 minutes / 900s)
- **Authentication Style**: Passwordless OTP via Email and SMS with cryptographic hashing (SHA-256) and Redis-ready rate-limiting

### Frontend Stack (iOS)
- **Framework**: Swift 5.10, SwiftUI, Async/Await
- **Security**: Keychain-backed `TokenStore` for Bearer access token storage
- **UI Views**: `AuthFlowView`, `LandingView`, `LoginView`, `RegisterView`, `OTPVerificationView`, `ProfileView`, `SettingsView`

---

## 3. Authentication Audit

### Endpoint Traceability
| Endpoint | Method | Implemented | Description |
| :--- | :--- | :--- | :--- |
| `/api/v1/auth/otp/request` | POST | **PASS** | Generates & dispatches 6-digit OTP for LOGIN, REGISTRATION, or ACCOUNT_RECOVERY |
| `/api/v1/auth/otp/verify` | POST | **PASS** | Verifies OTP code, provisions/resolves User, creates `AuthSession`, returns JWT |
| `/api/v1/auth/refresh` | POST | **MISSING** | No refresh token endpoint exists; `refreshTokenHash` is stored as a dummy hash |
| `/api/v1/auth/logout` | POST | **MISSING** | No server-side session revocation endpoint exists; client simply discards JWT |
| `/api/v1/auth/revoke` | POST | **MISSING** | No endpoint to invalidate specific sessions or remote devices |

### Authentication Lifecycle Assessment
- **SIGNUP**: **PASS**. Handled via OTP request (`purpose=REGISTRATION`) and OTP verification, which registers a new `User` record with default `ACTIVE` status and assigns the `USER` role.
- **ACCOUNT CREATION**: **PASS**. Auto-provisions default user display name from email/phone.
- **PASSWORD STORAGE**: **NOT APPLICABLE**. Platform uses 100% passwordless OTP authentication.
- **LOGIN**: **PASS**. OTP verification succeeds only for registered active accounts.
- **TOKEN/SESSION CREATION**: **PARTIAL**. Inserts record in `auth_sessions` table and returns signed JWT access token. Refresh token is unhandled (dummy string stored).
- **AUTHORIZED REQUEST**: **PASS**. `JwtAuthenticationFilter` intercepts `Authorization: Bearer <token>`, validates signature and expiration, builds `CommunityOttPrincipal`, and evaluates method-level permissions.
- **LOGOUT & REVOCATION**: **MISSING**. Server receives no logout signal; JWT remains cryptographically valid until expiration.

---

## 4. Security Audit

- **Password Hashing / Storage**: **NOT REQUIRED** (Passwordless OTP system).
- **Password Leakage**: **PASS** (Zero password fields exist in DB or DTOs).
- **JWT Token Validation**: **IMPLEMENTED** (Validates HMAC-SHA256 signature, expiry, issuer `communityott-backend`, audience `communityott-clients`).
- **Refresh Token Security**: **MISSING** (No refresh token issue/rotate mechanism).
- **Session Expiration**: **PARTIAL** (`auth_sessions` has `expires_at` [30 days], but `JwtAuthenticationFilter` never queries `auth_sessions` during API authorization).
- **Session Revocation**: **MISSING** (`revoked_at` column exists in DB, but `JwtAuthenticationFilter` does not check revocation status).
- **Account Lockout**: **PARTIAL** (Locks OTP request for 15 minutes after 3 failed verification attempts).
- **Brute-Force Protection**: **PARTIAL** (OTP attempt counters & 60-second cooldown per identifier).
- **Authorization Enforcement**: **IMPLEMENTED** (`@PreAuthorize("@rbacAuthorization.hasPermission(...)")` enforced on admin, manager, content, and video endpoints).
- **Cross-User Protection (IDOR)**: **IMPLEMENTED** (Profile, Saved Content, and Watch History APIs strictly query by authenticated `principal.getUserId()`).
- **Sensitive Data Leakage**: **IMPLEMENTED** (Structured `ApiResponse` and `GlobalExceptionHandler` sanitize error output and stack traces).
- **CORS Configuration**: **IMPLEMENTED** (Restricted via `communityott.security.allowed-origins`).
- **CSRF Protection**: **NOT REQUIRED** (Stateless REST API using Bearer headers; no session cookies used).

---

## 5. RBAC Audit

### Seeded System Roles & Permissions (`V3__seed_rbac_data.sql`)
1. **`SUPER_ADMIN`**: Unrestricted control. Granted all 31 system permissions across `user`, `role`, `permission`, `content`, `video`, `analytics`, `notification`, `audit`, and `system` modules.
2. **`MANAGER`**: Administrative oversight. Permissions: `USER_VIEW`, `CONTENT_VIEW`, `ANALYTICS_VIEW`, `ANALYTICS_EXPORT`, `NOTIFICATION_VIEW`, `NOTIFICATION_SEND`, `AUDIT_VIEW`, `SYSTEM_HEALTH_VIEW`.
3. **`CONTENT_MANAGER`**: Pipeline operations. Permissions: `CONTENT_VIEW`, `CONTENT_CREATE`, `CONTENT_UPDATE`, `CONTENT_SUBMIT`, `VIDEO_UPLOAD`, `VIDEO_VIEW`, `VIDEO_EDIT`, `VIDEO_PROCESS`, `VIDEO_RETRY`.
4. **`USER`**: Standard end-user subscriber. Permissions: `CONTENT_VIEW`, `VIDEO_VIEW`.

### Authorization Verification
- Endpoints utilize `@EnableMethodSecurity` and `@PreAuthorize("@rbacAuthorization.hasPermission(authentication, 'PERMISSION_NAME')")`.
- `USER` role is strictly restricted from administrative, analytics, and content management APIs.

---

## 6. Device Audit

- **Dedicated Device Table**: **MISSING** (No `devices` table or `Device` JPA entity exists).
- **Embedded Device Metadata**: Metadata (`device_id`, `device_name`, `platform`, `ip_address`, `user_agent`) is embedded inside `auth_sessions`, `playback_sessions`, and `watch_history`.
- **Device Management APIs**: **MISSING** (No endpoints to list user devices, rename devices, or remove devices).
- **Maximum Device Enforcements**: **MISSING** (No 2-device limit or max registered device rule is enforced during login or session creation).
- **Distinct OTT Concepts**:
  - **REGISTERED DEVICE**: Missing as an explicit entity.
  - **SESSION**: Implemented via `auth_sessions` table.
  - **CONCURRENT PLAYBACK**: Implemented via `playback_sessions` table (`V13`).
  - **DOWNLOAD DEVICE**: Missing.

---

## 7. Session Audit

- **Persistent Session Storage**: Implemented via `auth_sessions` table (`id`, `user_id`, `device_id`, `device_name`, `platform`, `refresh_token_hash`, `created_at`, `last_used_at`, `expires_at`, `revoked_at`, `ip_address`, `user_agent`).
- **Session Revocation Coupling**: **DECOUPLED / WEAK**. `JwtAuthenticationFilter` validates JWTs statelessly without inspecting `auth_sessions.revoked_at`. A revoked session in DB can still access APIs until the JWT expires.

---

## 8. Login History Audit

- **Login Audit Log Table**: **MISSING** (No `login_history` table exists).
- **OTP Audit Log**: `otp_requests` and `otp_delivery_attempts` track OTP creation and delivery attempts, but do not record login successes/failures or IP history for security audits.

---

## 9. Profile Audit

- **Profile Model**: Implemented in `profiles` table (`id`, `user_id`, `display_name`, `avatar_url`, `preferred_language`, `is_default`, `created_at`, `updated_at`).
- **Profile Management APIs**: **PASS** (`GET /api/v1/profiles`, `POST /api/v1/profiles`, `GET /api/v1/profiles/{id}`, `PUT /api/v1/profiles/{id}`, `DELETE /api/v1/profiles/{id}`).
- **Default Profile Logic**: Auto-assigns default profile on user creation and reassigns default profile if active default is deleted.
- **Profile Limits**: **MISSING** (No upper limit on profiles per user account, e.g. max 5 profiles).
- **Profile Type & Maturity**: **MISSING** (No `is_kids` flag, maturity level, or profile PIN).

---

## 10. Kids Space / Parental Control Audit

- **Kids Profile Flag**: **MISSING**
- **Maturity Rating Filter**: **MISSING** (Content table has `age_rating`, but no filtering logic per profile rating).
- **Parental PIN Lock**: **MISSING**
- **Kids Catalogue Endpoint**: **MISSING**

---

## 11. OTT Benchmark Research

| Feature Category | Benchmark (Netflix / Disney+ / Prime / Max) | CommunityOTT Existing Status | Gap Description |
| :--- | :--- | :--- | :--- |
| **Auth** | Passwordless OTP / Social + Refresh Token Rotation | OTP + Access Token | Missing `/auth/refresh` & `/auth/logout` |
| **Devices** | Registered Devices API, Device Limit (2-5 devices), Device Revocation | Metadata in `auth_sessions` | Missing `Device` entity, 2-device limit, and Device Management API |
| **Sessions** | Active Session List, "Sign Out All Devices", Revocation Filter | DB table `auth_sessions` | `JwtAuthenticationFilter` ignores session `revoked_at` status |
| **Profiles** | Max 5 Profiles, Custom Avatars, Language, Profile Watch History | CRUD Profiles, Default Profile | Missing Max 5 limit check, Profile-specific watchlist |
| **Kids Space** | Dedicated Kids Profile, Maturity Thresholds, Parental PIN | Age rating in `content` | Missing `is_kids` flag, PIN lock, Kids catalogue filter |

---

## 12. Gap Analysis

| Capability | CommunityOTT | OTT Benchmark | Gap | Priority |
| :--- | :--- | :--- | :--- | :--- |
| OTP Request & Verify | GREEN | Passwordless OTP | Fully implemented | P0 |
| JWT Access Tokens | GREEN | Short-lived Access Tokens | Fully implemented (15-min TTL) | P0 |
| Refresh Tokens & Logout | RED | Refresh Token Rotation & Logout | Missing endpoints `/auth/refresh` & `/auth/logout` | P0 |
| Session Revocation Enforcement | RED | Server-side revocation check | `JwtAuthenticationFilter` ignores `revoked_at` | P0 |
| Fine-Grained RBAC | GREEN | System & Custom Roles | 31 permissions across 4 system roles | P0 |
| Profile CRUD | GREEN | Multi-Profile Viewing | Supported via `/api/v1/profiles` | P1 |
| Max 5 Profile Limit | RED | Enforced Profile Limits | Missing limit check in `ProfileService` | P1 |
| Device Management & Limits | RED | Explicit Devices & 2-Device Rule | Missing `devices` table, 2-device limit, & device API | P1 |
| Login Audit History | RED | Login Activity & Failure Audit | Missing `login_history` table & recording | P2 |
| Kids Profile & Parental PIN | RED | Dedicated Kids Space & PIN | Missing `is_kids` flag, PIN lock, & Kids filter | P2 |

---

## 13. Quality Scores

- **Authentication**: **7 / 10** (Solid OTP generation, verification, and JWT signing; lacks refresh token rotation & logout API).
- **Security**: **7 / 10** (Stateless Spring Security 6, fine-grained RBAC, no PII/passwords logged; session revocation unlinked from JWT filter).
- **RBAC**: **9 / 10** (Comprehensive 31 system permissions, 4 system roles, method-level `@PreAuthorize` authorization).
- **Device Management**: **2 / 10** (Device info stored only as loose session strings; no device limit, no device management API).
- **Session Management**: **4 / 10** (DB table exists, but lacks logout/revocation endpoints, and filter ignores session revocation).
- **Login History**: **1 / 10** (No login audit history table or API exists).
- **Profiles**: **6 / 10** (Basic profile CRUD & default profile logic working; lacks profile limits, Kids flag, and PIN).
- **Kids Space**: **0 / 10** (Completely unimplemented).
- **Overall Account System**: **5.5 / 10** (Solid core foundation; requires OTT-specific hardening).

---

## 14. Recommended Implementation Order

- **NEXT 1: Auth & Session Hardening**
  - Implement `/api/v1/auth/refresh` with secure refresh token rotation.
  - Implement `/api/v1/auth/logout` and session revocation logic.
  - Update `JwtAuthenticationFilter` to verify active session status (`revoked_at == null` and `expires_at > now`).

- **NEXT 2: Device Management & 2-Device Limit**
  - Create explicit `Device` entity/table and Flyway migration.
  - Implement device registration and 2-device limit enforcement during login/session creation.
  - Create `/api/v1/devices` endpoints (list registered devices, revoke device, logout device).

- **NEXT 3: Profile Hardening & Limits**
  - Enforce maximum 5 profiles per user account.
  - Add `is_kids`, `avatar_id`, `maturity_rating`, and `parental_pin` fields to `Profile`.

- **NEXT 4: Kids Space & Parental Controls**
  - Implement Kids catalogue filtering based on profile `is_kids` and content `age_rating`.
  - Add parent authorization requirement for switching out of Kids profile or accessing restricted content.

- **NEXT 5: Login History & Security Audit Logging**
  - Create `login_history` table and record login success, failures, device ID, IP address, and user agent.
  - Expose `/api/v1/account/login-history` for user security transparency.

---

## 15. What NOT To Change Yet

1. **Do NOT alter existing Flyway migrations V1–V18**. All schema enhancements must be added as new Flyway migration scripts (e.g., `V19__...`).
2. **Do NOT break passwordless OTP authentication**. Do NOT add mandatory password fields to `User`.
3. **Do NOT remove development header auth (`X-Dev-User-Id`)**. It is essential for local development and integration tests when devAuthEnabled=true.
4. **Do NOT modify existing RBAC permission names** (`CONTENT_VIEW`, `VIDEO_UPLOAD`, `ANALYTICS_VIEW`, etc.).
