package com.communityott.content.entity;

import java.util.Set;

public enum ProcessingJobStatus {
    QUEUED,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean canTransitionTo(ProcessingJobStatus target) {
        if (this == target) {
            return true;
        }
        return switch (this) {
            case QUEUED -> Set.of(PROCESSING, CANCELLED).contains(target);
            case PROCESSING -> Set.of(COMPLETED, FAILED, CANCELLED).contains(target);
            case FAILED -> Set.of(QUEUED).contains(target); // retry allowed
            case COMPLETED, CANCELLED -> false; // Terminal states
        };
    }
}
