package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

public class AnalyticsInvalidPlatformException extends ApiException {

    public AnalyticsInvalidPlatformException(String platform) {
        super("Invalid platform: '" + platform + "'. Supported platforms are: IOS, ANDROID, WEB",
                HttpStatus.BAD_REQUEST, "ANALYTICS_INVALID_PLATFORM");
    }
}
