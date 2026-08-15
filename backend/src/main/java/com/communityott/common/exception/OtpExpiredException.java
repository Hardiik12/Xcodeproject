package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

public class OtpExpiredException extends ApiException {
    public OtpExpiredException() {
        super("The OTP code has expired or is invalid. Please request a new OTP", HttpStatus.BAD_REQUEST, "OTP_EXPIRED");
    }
}
