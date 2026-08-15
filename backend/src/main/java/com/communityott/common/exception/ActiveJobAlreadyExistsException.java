package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

public class ActiveJobAlreadyExistsException extends ApiException {
    public ActiveJobAlreadyExistsException(Long videoAssetId, String jobType) {
        super("An active " + jobType + " job already exists for video asset ID " + videoAssetId,
                HttpStatus.CONFLICT,
                "ACTIVE_PROCESSING_JOB_EXISTS");
    }
}
