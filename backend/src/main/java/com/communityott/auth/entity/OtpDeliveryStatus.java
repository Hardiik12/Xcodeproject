package com.communityott.auth.entity;

/**
 * Lifecycle status of an individual OTP delivery attempt.
 */
public enum OtpDeliveryStatus {
    PENDING,
    SENT,
    FAILED
}
