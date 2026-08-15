package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

public class ContentNotFoundException extends ApiException {

    public ContentNotFoundException(Long contentId) {
        super("Content item with ID " + contentId + " was not found", HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND");
    }
}
