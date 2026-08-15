package com.communityott.content.processing;

public interface VideoProcessor {

    /**
     * Executes the processing pipeline for the given VideoProcessingJob ID.
     *
     * @param jobId The database primary key of VideoProcessingJob
     */
    void process(Long jobId);
}
