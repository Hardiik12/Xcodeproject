package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

public class VideoAssetNotFoundException extends ApiException {
    public VideoAssetNotFoundException(Long id) {
        super("Video asset not found with id: " + id, HttpStatus.NOT_FOUND, "VIDEO_ASSET_NOT_FOUND");
    }

    public VideoAssetNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, "VIDEO_ASSET_NOT_FOUND");
    }
}
