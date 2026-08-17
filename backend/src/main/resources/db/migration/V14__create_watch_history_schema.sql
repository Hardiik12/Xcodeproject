-- ==============================================================================
-- CommunityOTT Monolithic Backend - V14: Create Watch History Schema
-- ==============================================================================

CREATE TABLE IF NOT EXISTS watch_history (
    id                      BIGSERIAL                   PRIMARY KEY,
    user_id                 BIGINT                      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content_id              BIGINT                      NOT NULL REFERENCES content(id) ON DELETE CASCADE,
    playback_session_id     VARCHAR(64),
    watched_seconds         INTEGER                     NOT NULL DEFAULT 0,
    duration_seconds        INTEGER                     NOT NULL DEFAULT 0,
    completion_percentage   DOUBLE PRECISION            NOT NULL DEFAULT 0.0,
    completed               BOOLEAN                     NOT NULL DEFAULT FALSE,
    device_id               VARCHAR(255),
    platform                VARCHAR(32)                 NOT NULL DEFAULT 'WEB',
    first_watched_at        TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_watched_at         TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at              TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version                 BIGINT                      NOT NULL DEFAULT 0,

    CONSTRAINT uq_watch_history_user_content UNIQUE (user_id, content_id)
);

CREATE INDEX IF NOT EXISTS idx_watch_history_user_last_watched
    ON watch_history (user_id, last_watched_at DESC);

CREATE INDEX IF NOT EXISTS idx_watch_history_content_id
    ON watch_history (content_id);

CREATE INDEX IF NOT EXISTS idx_watch_history_session_id
    ON watch_history (playback_session_id);
