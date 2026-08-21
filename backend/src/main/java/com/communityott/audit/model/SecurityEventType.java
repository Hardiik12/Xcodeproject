package com.communityott.audit.model;

/**
 * Standardized security event taxonomy enum for CommunityOTT audit logging.
 */
public enum SecurityEventType {
    // Authentication Events
    AUTHN_OTP_REQUESTED,
    AUTHN_OTP_DELIVERED,
    AUTHN_OTP_FAILED,
    AUTHN_OTP_EXPIRED,
    AUTHN_LOGIN_SUCCESS,
    AUTHN_LOGIN_FAILED,

    // Session Events
    SESSION_CREATED,
    SESSION_REFRESH_SUCCESS,
    SESSION_REFRESH_FAILED,
    SESSION_LOGOUT,
    SESSION_LOGOUT_ALL,
    SESSION_EXPIRED,
    SESSION_REVOKED,

    // Device Security Events
    DEVICE_REGISTERED,
    DEVICE_REACTIVATED,
    DEVICE_REVOKED,
    DEVICE_REPLACED,
    DEVICE_LIMIT_REACHED,

    // Authorization & Threat Events
    AUTHZ_DENIED,
    SECURITY_TOKEN_REUSE,
    SECURITY_INVALID_TOKEN,
    SECURITY_IDOR_ATTEMPT,
    SECURITY_SUSPICIOUS_ACTIVITY
}
