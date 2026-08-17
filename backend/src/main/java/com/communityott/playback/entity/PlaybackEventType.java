package com.communityott.playback.entity;

/**
 * Strongly typed enumeration of OTT client playback telemetry events.
 */
public enum PlaybackEventType {
    /**
     * Client initiated playback of the media stream.
     */
    PLAY,

    /**
     * User or system paused media playback.
     */
    PAUSE,

    /**
     * Playback resumed after being paused or stalled.
     */
    RESUME,

    /**
     * User jumped/scrubbed to a different timestamp.
     */
    SEEK,

    /**
     * Player entered buffering / re-buffering state due to empty jitter buffers.
     */
    BUFFER_START,

    /**
     * Player exited buffering state and resumed playback delivery.
     */
    BUFFER_END,

    /**
     * ABR (Adaptive Bitrate) rendition switch or manual resolution selection.
     */
    QUALITY_CHANGE,

    /**
     * Unrecoverable player error (decoder failure, network timeout, segment fetch 404).
     */
    ERROR,

    /**
     * Periodic client heartbeat emitted during continuous playback.
     */
    HEARTBEAT,

    /**
     * Media reached the final credits / completion threshold (>= 95%).
     */
    COMPLETE,

    /**
     * Viewing session gracefully terminated / player dismissed by user.
     */
    END
}
