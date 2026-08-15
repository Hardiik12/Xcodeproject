package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an account registration is attempted for an already registered identifier.
 */
public class AuthRegistrationNotAllowedException extends ApiException {

    public AuthRegistrationNotAllowedException(String message) {
        super(message, HttpStatus.CONFLICT, "AUTH_REGISTRATION_NOT_ALLOWED");
    }

    public AuthRegistrationNotAllowedException() {
        this("An account with this identifier already exists. Please log in.");
    }
}
