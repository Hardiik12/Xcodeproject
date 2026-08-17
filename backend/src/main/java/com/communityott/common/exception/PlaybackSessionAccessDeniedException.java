package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a user attempts to access or modify a playback session belonging to another user.
 */
public class PlaybackSessionAccessDeniedException extends ApiException {

    public PlaybackSessionAccessDeniedException(String sessionId) {
        super("Access denied to playback session: " + sessionId, HttpStatus.FORBIDDEN, "PLAYBACK_SESSION_ACCESS_DENIED");
    }

    public PlaybackSessionAccessDeniedException(String message, HttpStatus status) {
        super(message, status, "PLAYBACK_SESSION_ACCESS_DENIED");
    }
}
