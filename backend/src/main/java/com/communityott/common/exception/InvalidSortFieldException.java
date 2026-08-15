package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidSortFieldException extends ApiException {

    public InvalidSortFieldException(String fieldName, java.util.Collection<String> allowedFields) {
        super("Sort field '" + fieldName + "' is invalid. Allowed fields: " + String.join(", ", allowedFields), HttpStatus.BAD_REQUEST, "INVALID_SORT_FIELD");
    }
}
