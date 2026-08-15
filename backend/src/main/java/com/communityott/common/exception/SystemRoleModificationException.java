package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

public class SystemRoleModificationException extends ApiException {
    public SystemRoleModificationException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "SYSTEM_ROLE_PROTECTED");
    }
}
