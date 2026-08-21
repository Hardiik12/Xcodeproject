# Phase A — Authentication & Session Hardening Architecture

## Executive Summary

This document details the operational architecture, session lifecycle, token binding, token rotation, reuse detection compromise handling, and session revocation logic implemented in the CommunityOTT backend service.

---

## 1. Authentication & Session Lifecycle

```
Client (OTP Request)
      ↓
Client (OTP Verify: /api/v1/auth/otp/verify)
      ↓
Create AuthSession (auth_sessions DB)
      ↓
Generate Cryptographic Refresh Token (64-byte SecureRandom, Base64Url)
      ↓
Persist SHA-256 Refresh Token Hash (refresh_token_hash)
      ↓
Generate JWT Access Token (subject: userId, claim sid: sessionId, TTL: 15 mins)
      ↓
Return AuthenticationResponse (access_token, refresh_token, token_type, expires_in)
```

---

## 2. Token Rotation & Reuse Detection

### Token Refresh (`POST /api/v1/auth/refresh`)
1. Client submits raw `refresh_token`.
2. Server computes $H(\text{refresh\_token}) = \text{SHA-256}(\text{refresh\_token})$.
3. **Primary Lookup**: Search `auth_sessions` by `refresh_token_hash == H(token)`.
4. **Reuse Protection Check**: If not found, search `previous_refresh_token_hash == H(token)`.
   - **Compromise Alert**: If matched in `previous_refresh_token_hash`, a previously rotated token was reused.
   - **Immediate Revocation**: The server immediately revokes the session (`revoked_at = now()`) and throws `AuthTokenReuseException` (HTTP 401).
5. **Session Active Verification**: Verifies `revoked_at == null` and `expires_at > now()`.
6. **Rotation**:
   - Compute new raw `new_refresh_token` and hash $H(\text{new\_refresh\_token})$.
   - Update `previous_refresh_token_hash = old_refresh_token_hash`.
   - Update `refresh_token_hash = H(new_refresh_token)`.
   - Update `last_used_at = now()`.
   - Issue new JWT Access Token containing updated session binding.

---

## 3. Session Revocation & Access Token Binding

### Access Token Claim Binding
JWT access tokens include standard subject and session claims:
- `sub`: User ID
- `userId`: User ID
- `sid`: Session ID (`auth_sessions.id`)

### Security Filter Session Revocation Enforcement (`JwtAuthenticationFilter`)
After cryptographic JWT signature, expiration, issuer, and audience validation:
1. Extract `sid` (Session ID) from JWT claim.
2. Load corresponding session from `auth_sessions`.
3. Validate:
   - Session exists in database.
   - Session `user_id` matches JWT subject `userId`.
   - Session `revoked_at` is null.
   - Session `expires_at` is after current timestamp.
4. If session is revoked, expired, or invalid, authentication is rejected (HTTP 401 Unauthorized).

---

## 4. Logout & Remote Session Invalidation

- **Single Session Logout (`POST /api/v1/auth/logout`)**: Sets `revoked_at = now()` for the current session. Subsequent API requests using the access token or refresh token fail with HTTP 401.
- **Logout All Sessions (`POST /api/v1/auth/logout-all`)**: Revokes all active sessions (`findActiveSessionsByUserId`) belonging to the authenticated user. Invalidates all active access tokens and refresh tokens across all devices for that user.

---

## 5. Security & Error Matrix

| Event / Scenario | HTTP Status | Error Code | Response / Action |
| :--- | :--- | :--- | :--- |
| Missing/Blank Refresh Token | 400 Bad Request | `VALIDATION_ERROR` | Validation error payload |
| Invalid / Unrecognized Token | 401 Unauthorized | `INVALID_REFRESH_TOKEN` | Generic authentication failure |
| Expired Session | 401 Unauthorized | `AUTH_SESSION_EXPIRED` | Auth session expired payload |
| Revoked Session | 401 Unauthorized | `AUTH_SESSION_REVOKED` | Session revoked payload |
| Refresh Token Reuse | 401 Unauthorized | `REFRESH_TOKEN_REUSED` | Immediate session revocation |
| Revoked Access Token Session | 401 Unauthorized | `UNAUTHORIZED` | SecurityContext unauthenticated |
