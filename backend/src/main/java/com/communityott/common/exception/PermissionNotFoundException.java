package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

public class PermissionNotFoundException extends ApiException {
    public PermissionNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, "PERMISSION_NOT_FOUND");
    }

    public PermissionNotFoundException(Long permissionId) {
        super("Permission not found with ID: " + permissionId, HttpStatus.NOT_FOUND, "PERMISSION_NOT_FOUND");
    }
}
