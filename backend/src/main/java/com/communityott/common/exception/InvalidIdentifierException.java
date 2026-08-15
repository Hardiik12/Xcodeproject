package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidIdentifierException extends ApiException {
    public InvalidIdentifierException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "OTP_IDENTIFIER_INVALID");
    }
}
