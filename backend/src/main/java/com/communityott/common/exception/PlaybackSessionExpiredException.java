package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when attempting to interact with an expired playback session.
 */
public class PlaybackSessionExpiredException extends ApiException {

    public PlaybackSessionExpiredException(String sessionId) {
        super("Playback session has expired: " + sessionId, HttpStatus.CONFLICT, "PLAYBACK_SESSION_EXPIRED");
    }

    public PlaybackSessionExpiredException(String message, HttpStatus status) {
        super(message, status, "PLAYBACK_SESSION_EXPIRED");
    }
}
