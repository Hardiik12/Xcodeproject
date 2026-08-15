package com.communityott.common.exception;

import com.communityott.content.entity.ContentStatus;
import org.springframework.http.HttpStatus;

public class InvalidContentStateTransitionException extends ApiException {

    public InvalidContentStateTransitionException(ContentStatus currentStatus, ContentStatus targetStatus) {
        super("Cannot transition content from " + currentStatus + " to " + targetStatus,
                HttpStatus.BAD_REQUEST, "INVALID_CONTENT_STATE_TRANSITION");
    }

    public InvalidContentStateTransitionException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "INVALID_CONTENT_STATE_TRANSITION");
    }
}
