package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

public class ProfileNotFoundException extends ApiException {

    public ProfileNotFoundException(Long profileId) {
        super("Profile with ID " + profileId + " not found or does not belong to the user", HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND");
    }
}
