package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

public class AuthTokenReuseException extends ApiException {

    public AuthTokenReuseException() {
        super("Security alert: Refresh token reuse detected. Session has been revoked.", HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_REUSED");
    }

    public AuthTokenReuseException(String message) {
        super(message, HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_REUSED");
    }
}
