package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

public class OtpAlreadyUsedException extends ApiException {
    public OtpAlreadyUsedException() {
        super("This OTP has already been verified and used. Replay is not permitted", HttpStatus.BAD_REQUEST, "OTP_ALREADY_USED");
    }
}
