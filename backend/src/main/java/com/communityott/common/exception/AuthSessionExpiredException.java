package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

public class AuthSessionExpiredException extends ApiException {

    public AuthSessionExpiredException() {
        super("Auth session has expired. Please authenticate again.", HttpStatus.UNAUTHORIZED, "AUTH_SESSION_EXPIRED");
    }

    public AuthSessionExpiredException(String message) {
        super(message, HttpStatus.UNAUTHORIZED, "AUTH_SESSION_EXPIRED");
    }
}
