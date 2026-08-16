package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

public class VideoNotReadyException extends ApiException {

    public VideoNotReadyException(String message) {
        super(message, HttpStatus.CONFLICT, "VIDEO_NOT_READY");
    }

    public VideoNotReadyException(Long videoAssetId, String status) {
        super(String.format("Video asset ID %d is not ready for playback. Status: %s", videoAssetId, status),
                HttpStatus.CONFLICT, "VIDEO_NOT_READY");
    }
}
