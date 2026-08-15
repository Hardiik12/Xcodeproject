package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

public class VideoStorageException extends ApiException {
    public VideoStorageException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, "VIDEO_STORAGE_ERROR");
    }

    public VideoStorageException(String message, Throwable cause) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, "VIDEO_STORAGE_ERROR", cause);
    }
}
