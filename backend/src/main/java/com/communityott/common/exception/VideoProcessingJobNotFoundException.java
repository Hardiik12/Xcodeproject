package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

public class VideoProcessingJobNotFoundException extends ApiException {
    public VideoProcessingJobNotFoundException(Long jobId) {
        super("Video processing job with ID " + jobId + " not found", HttpStatus.NOT_FOUND, "VIDEO_PROCESSING_JOB_NOT_FOUND");
    }
}
