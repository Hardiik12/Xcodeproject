package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an account has been deleted and authentication is forbidden.
 */
public class AuthAccountDeletedException extends ApiException {

    public AuthAccountDeletedException(String message) {
        super(message, HttpStatus.FORBIDDEN, "AUTH_ACCOUNT_DELETED");
    }

    public AuthAccountDeletedException() {
        this("Account has been deleted.");
    }
}
