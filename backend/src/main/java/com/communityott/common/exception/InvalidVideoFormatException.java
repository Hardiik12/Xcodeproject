package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidVideoFormatException extends ApiException {
    public InvalidVideoFormatException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "INVALID_VIDEO_FORMAT");
    }
}
