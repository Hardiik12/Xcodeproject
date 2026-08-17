package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when attempting to record heartbeat or progress on a closed/non-active playback session.
 */
public class PlaybackSessionNotActiveException extends ApiException {

    public PlaybackSessionNotActiveException(String sessionId, String status) {
        super(String.format("Playback session '%s' is not active (current status: %s)", sessionId, status),
                HttpStatus.CONFLICT, "PLAYBACK_SESSION_NOT_ACTIVE");
    }

    public PlaybackSessionNotActiveException(String message) {
        super(message, HttpStatus.CONFLICT, "PLAYBACK_SESSION_NOT_ACTIVE");
    }
}
