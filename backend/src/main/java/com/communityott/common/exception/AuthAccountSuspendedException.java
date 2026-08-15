package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an account is suspended and authentication is forbidden.
 */
public class AuthAccountSuspendedException extends ApiException {

    public AuthAccountSuspendedException(String message) {
        super(message, HttpStatus.FORBIDDEN, "AUTH_ACCOUNT_SUSPENDED");
    }

    public AuthAccountSuspendedException() {
        this("Account is suspended. Please contact customer support.");
    }
}
