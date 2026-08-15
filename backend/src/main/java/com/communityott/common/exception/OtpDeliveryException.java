package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an OTP could not be delivered to the user's destination (email or phone).
 * Maps to HTTP 502 Bad Gateway to signify external/upstream delivery failure.
 */
public class OtpDeliveryException extends ApiException {

    public OtpDeliveryException(String message) {
        super(message, HttpStatus.BAD_GATEWAY, "OTP_DELIVERY_FAILED");
    }

    public OtpDeliveryException(String message, Throwable cause) {
        super(message, HttpStatus.BAD_GATEWAY, "OTP_DELIVERY_FAILED", cause);
    }

    public OtpDeliveryException() {
        super("Failed to deliver OTP to the provided identifier", HttpStatus.BAD_GATEWAY, "OTP_DELIVERY_FAILED");
    }
}
