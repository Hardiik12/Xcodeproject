package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

public class ContentNotAvailableForPlaybackException extends ApiException {

    public ContentNotAvailableForPlaybackException(String message) {
        super(message, HttpStatus.CONFLICT, "CONTENT_NOT_AVAILABLE");
    }

    public ContentNotAvailableForPlaybackException(Long contentId, String currentStatus) {
        super(String.format("Content ID %d is not available for playback. Current status: %s", contentId, currentStatus),
                HttpStatus.CONFLICT, "CONTENT_NOT_AVAILABLE");
    }
}
