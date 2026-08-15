package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

public class LastSuperAdminProtectionException extends ApiException {
    public LastSuperAdminProtectionException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "LAST_SUPER_ADMIN_PROTECTED");
    }

    public LastSuperAdminProtectionException() {
        this("Cannot remove SUPER_ADMIN role from the last remaining Super Admin user");
    }
}
