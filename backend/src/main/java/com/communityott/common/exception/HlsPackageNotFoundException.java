package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

public class HlsPackageNotFoundException extends ApiException {
    public HlsPackageNotFoundException(Long videoAssetId) {
        super("HLS package not found for video asset ID: " + videoAssetId, HttpStatus.NOT_FOUND, "HLS_PACKAGE_NOT_FOUND");
    }
}
