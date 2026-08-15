package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

public class UserNotFoundException extends ApiException {
    public UserNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, "USER_NOT_FOUND");
    }

    public UserNotFoundException(Long userId) {
        super("User not found with ID: " + userId, HttpStatus.NOT_FOUND, "USER_NOT_FOUND");
    }
}
