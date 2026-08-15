package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

public class CategoryNotFoundException extends ApiException {

    public CategoryNotFoundException(Long categoryId) {
        super("Category with ID " + categoryId + " was not found", HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND");
    }

    public CategoryNotFoundException(String identifier) {
        super("Category with identifier '" + identifier + "' was not found", HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND");
    }
}
