package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

public class LanguageDuplicateException extends ApiException {

    public LanguageDuplicateException(String message) {
        super(message, HttpStatus.CONFLICT, "LANGUAGE_DUPLICATE");
    }
}
