package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

public class CategoryDuplicateException extends ApiException {

    public CategoryDuplicateException(String message) {
        super(message, HttpStatus.CONFLICT, "CATEGORY_DUPLICATE");
    }
}
