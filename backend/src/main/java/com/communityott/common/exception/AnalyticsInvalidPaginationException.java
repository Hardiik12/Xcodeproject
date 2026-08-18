package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

public class AnalyticsInvalidPaginationException extends ApiException {

    public AnalyticsInvalidPaginationException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "ANALYTICS_INVALID_PAGINATION");
    }
}
