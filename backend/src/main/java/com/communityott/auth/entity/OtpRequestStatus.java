package com.communityott.auth.entity;

/**
 * Status lifecycle states for an OTP request audit record.
 */
public enum OtpRequestStatus {
    REQUESTED,
    VERIFIED,
    EXPIRED,
    FAILED,
    LOCKED
}
