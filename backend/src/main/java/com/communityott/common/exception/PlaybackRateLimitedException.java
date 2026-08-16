package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

public class PlaybackRateLimitedException extends ApiException {

    public PlaybackRateLimitedException(String message) {
        super(message, HttpStatus.TOO_MANY_REQUESTS, "PLAYBACK_RATE_LIMITED");
    }
}
