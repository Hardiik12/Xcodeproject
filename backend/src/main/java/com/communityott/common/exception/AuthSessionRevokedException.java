package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

public class AuthSessionRevokedException extends ApiException {

    public AuthSessionRevokedException() {
        super("Auth session has been revoked. Please authenticate again.", HttpStatus.UNAUTHORIZED, "AUTH_SESSION_REVOKED");
    }

    public AuthSessionRevokedException(String message) {
        super(message, HttpStatus.UNAUTHORIZED, "AUTH_SESSION_REVOKED");
    }
}
