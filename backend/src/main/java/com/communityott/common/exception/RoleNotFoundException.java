package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

public class RoleNotFoundException extends ApiException {
    public RoleNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, "ROLE_NOT_FOUND");
    }

    public RoleNotFoundException(Long roleId) {
        super("Role not found with ID: " + roleId, HttpStatus.NOT_FOUND, "ROLE_NOT_FOUND");
    }
}
