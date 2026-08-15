package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

public class ContentVersionConflictException extends ApiException {

    public ContentVersionConflictException(Long contentId) {
        super("Content with ID " + contentId + " was modified by another user. Please refresh and try again.",
                HttpStatus.CONFLICT, "CONTENT_VERSION_CONFLICT");
    }

    public ContentVersionConflictException(String message) {
        super(message, HttpStatus.CONFLICT, "CONTENT_VERSION_CONFLICT");
    }
}
