package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

public class VideoProcessingException extends ApiException {
    public VideoProcessingException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, "VIDEO_PROCESSING_ERROR");
    }

    public VideoProcessingException(String message, Throwable cause) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, "VIDEO_PROCESSING_ERROR", cause);
    }
}
