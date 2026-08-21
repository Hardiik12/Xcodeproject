package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a requested security alert does not exist or does not belong to the user.
 */
public class SecurityAlertNotFoundException extends ApiException {
    public SecurityAlertNotFoundException(Long alertId) {
        super("Security alert not found with ID: " + alertId, HttpStatus.NOT_FOUND, "SECURITY_ALERT_NOT_FOUND");
    }

    public SecurityAlertNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, "SECURITY_ALERT_NOT_FOUND");
    }
}
