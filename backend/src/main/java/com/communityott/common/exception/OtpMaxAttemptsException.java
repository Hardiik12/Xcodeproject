package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

public class OtpMaxAttemptsException extends ApiException {
    public OtpMaxAttemptsException() {
        super("Maximum verification attempts exceeded. OTP is locked. Please request a new OTP", HttpStatus.BAD_REQUEST, "OTP_MAX_ATTEMPTS");
    }
}
