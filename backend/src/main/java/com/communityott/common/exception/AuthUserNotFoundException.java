package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an account does not exist for the provided identifier.
 */
public class AuthUserNotFoundException extends ApiException {

    public AuthUserNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, "AUTH_USER_NOT_FOUND");
    }

    public AuthUserNotFoundException() {
        this("Account not found for the provided identifier");
    }
}
