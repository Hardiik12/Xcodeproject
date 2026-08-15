package com.communityott.common.exception;

import com.communityott.content.entity.ContentStatus;
import org.springframework.http.HttpStatus;

public class ContentInvalidStatusTransitionException extends ApiException {

    public ContentInvalidStatusTransitionException(ContentStatus currentStatus, ContentStatus targetStatus) {
        super("Cannot transition content status from " + currentStatus + " to " + targetStatus,
                HttpStatus.BAD_REQUEST,
                "CONTENT_INVALID_STATUS_TRANSITION");
    }
}
