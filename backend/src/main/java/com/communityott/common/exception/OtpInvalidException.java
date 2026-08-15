package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

public class OtpInvalidException extends ApiException {
    public OtpInvalidException(int remainingAttempts) {
        super("Invalid OTP code. Remaining attempts: " + remainingAttempts, HttpStatus.BAD_REQUEST, "OTP_INVALID");
    }

    public OtpInvalidException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "OTP_INVALID");
    }
}
