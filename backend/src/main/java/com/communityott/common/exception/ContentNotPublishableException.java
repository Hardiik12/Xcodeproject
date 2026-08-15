package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

import java.util.List;

public class ContentNotPublishableException extends ApiException {

    public ContentNotPublishableException(String reason) {
        super("Content is not ready to be published: " + reason, HttpStatus.BAD_REQUEST, "CONTENT_NOT_PUBLISHABLE");
    }

    public ContentNotPublishableException(List<String> reasons) {
        super("Content is not ready to be published: " + String.join("; ", reasons), HttpStatus.BAD_REQUEST, "CONTENT_NOT_PUBLISHABLE");
    }
}
