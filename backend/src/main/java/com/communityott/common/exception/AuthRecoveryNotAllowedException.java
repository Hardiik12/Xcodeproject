package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when account recovery is attempted for a non-existent or invalid account.
 */
public class AuthRecoveryNotAllowedException extends ApiException {

    public AuthRecoveryNotAllowedException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "AUTH_RECOVERY_NOT_ALLOWED");
    }

    public AuthRecoveryNotAllowedException() {
        this("Account recovery is only available for existing registered accounts.");
    }
}
