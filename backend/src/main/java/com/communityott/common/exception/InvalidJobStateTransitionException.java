package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidJobStateTransitionException extends ApiException {
    public InvalidJobStateTransitionException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "INVALID_JOB_STATE_TRANSITION");
    }
}
