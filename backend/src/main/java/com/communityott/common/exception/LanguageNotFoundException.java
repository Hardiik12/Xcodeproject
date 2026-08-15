package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

public class LanguageNotFoundException extends ApiException {

    public LanguageNotFoundException(Long languageId) {
        super("Language with ID " + languageId + " was not found", HttpStatus.NOT_FOUND, "LANGUAGE_NOT_FOUND");
    }

    public LanguageNotFoundException(String identifier) {
        super("Language with identifier '" + identifier + "' was not found", HttpStatus.NOT_FOUND, "LANGUAGE_NOT_FOUND");
    }
}
