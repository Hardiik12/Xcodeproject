package com.communityott.playback.entity;

/**
 * State machine representing the lifecycle of an OTT video playback session.
 */
public enum PlaybackSessionStatus {
    STARTED,
    ACTIVE,
    PAUSED,
    ENDED,
    EXPIRED;

    /**
     * Validates whether a state transition from current state to next state is permitted.
     *
     * @param targetStatus Target state to transition into
     * @return {@code true} if transition is valid; {@code false} otherwise
     */
    public boolean canTransitionTo(PlaybackSessionStatus targetStatus) {
        if (targetStatus == null) {
            return false;
        }

        // Idempotent transitions
        if (this == targetStatus) {
            return true;
        }

        return switch (this) {
            case STARTED -> targetStatus == ACTIVE || targetStatus == PAUSED || targetStatus == ENDED || targetStatus == EXPIRED;
            case ACTIVE -> targetStatus == PAUSED || targetStatus == ENDED || targetStatus == EXPIRED;
            case PAUSED -> targetStatus == ACTIVE || targetStatus == ENDED || targetStatus == EXPIRED;
            case ENDED, EXPIRED -> false; // Terminal states
        };
    }
}
