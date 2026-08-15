package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

public class VideoUploadSizeExceededException extends ApiException {
    public VideoUploadSizeExceededException(long actualSize, long maxSize) {
        super(String.format("File size %d bytes exceeds maximum permitted size of %d bytes", actualSize, maxSize),
                HttpStatus.BAD_REQUEST, "VIDEO_UPLOAD_SIZE_EXCEEDED");
    }
}
