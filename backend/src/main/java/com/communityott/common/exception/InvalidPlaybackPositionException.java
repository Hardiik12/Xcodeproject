package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a playback position update contains an invalid or out-of-bounds value.
 */
public class InvalidPlaybackPositionException extends ApiException {

    public InvalidPlaybackPositionException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "INVALID_PLAYBACK_POSITION");
    }

    public InvalidPlaybackPositionException(int position, int duration) {
        super(String.format("Invalid playback position: %d seconds exceeds video duration of %d seconds", position, duration),
                HttpStatus.BAD_REQUEST, "INVALID_PLAYBACK_POSITION");
    }
}
