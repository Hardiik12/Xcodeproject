package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidRefreshTokenException extends ApiException {

    public InvalidRefreshTokenException() {
        super("Invalid or malformed refresh token", HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN");
    }

    public InvalidRefreshTokenException(String message) {
        super(message, HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN");
    }
}
