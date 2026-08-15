package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

public class RoleAlreadyExistsException extends ApiException {
    public RoleAlreadyExistsException(String roleName) {
        super("Role already exists with name: " + roleName, HttpStatus.CONFLICT, "ROLE_ALREADY_EXISTS");
    }
}
