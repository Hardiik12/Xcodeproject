package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

public class OtpRateLimitedException extends ApiException {
    public OtpRateLimitedException(String message) {
        super(message, HttpStatus.TOO_MANY_REQUESTS, "OTP_RATE_LIMITED");
    }
}
