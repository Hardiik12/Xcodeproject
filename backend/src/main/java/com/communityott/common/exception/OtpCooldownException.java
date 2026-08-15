package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

public class OtpCooldownException extends ApiException {
    public OtpCooldownException(long resendAfterSeconds) {
        super("Please wait " + resendAfterSeconds + " seconds before requesting another OTP", HttpStatus.TOO_MANY_REQUESTS, "OTP_COOLDOWN");
    }
}
