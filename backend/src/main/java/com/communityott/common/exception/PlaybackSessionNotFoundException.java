package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a requested playback session cannot be found.
 */
public class PlaybackSessionNotFoundException extends ApiException {

    public PlaybackSessionNotFoundException(String sessionId) {
        super("Playback session not found: " + sessionId, HttpStatus.NOT_FOUND, "PLAYBACK_SESSION_NOT_FOUND");
    }

    public PlaybackSessionNotFoundException(String message, HttpStatus status) {
        super(message, status, "PLAYBACK_SESSION_NOT_FOUND");
    }
}
