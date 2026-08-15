package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

public class ContentNotPublishedException extends ApiException {

    public ContentNotPublishedException(Long contentId) {
        super("Content item with ID " + contentId + " is not currently available", HttpStatus.NOT_FOUND, "CONTENT_NOT_PUBLISHED");
    }
}
