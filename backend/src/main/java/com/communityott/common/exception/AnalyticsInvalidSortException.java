package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

public class AnalyticsInvalidSortException extends ApiException {

    public AnalyticsInvalidSortException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "ANALYTICS_INVALID_SORT");
    }
}
