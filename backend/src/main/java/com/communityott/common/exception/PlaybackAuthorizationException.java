package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

public class PlaybackAuthorizationException extends ApiException {

    public PlaybackAuthorizationException(String message) {
        super(message, HttpStatus.FORBIDDEN, "PLAYBACK_NOT_AUTHORIZED");
    }
}
