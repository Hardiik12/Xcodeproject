package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidDateRangeException extends ApiException {

    public InvalidDateRangeException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE");
    }
}
