-- ====================================================================
-- Phase 6.5: Playback Event + Telemetry Pipeline Schema
-- ====================================================================

CREATE TABLE IF NOT EXISTS playback_events (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    playback_session_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content_id BIGINT NOT NULL,
    video_asset_id BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    position_seconds INTEGER NOT NULL DEFAULT 0,
    duration_seconds INTEGER NOT NULL DEFAULT 0,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    platform VARCHAR(32) NOT NULL DEFAULT 'WEB',
    device_id VARCHAR(255),
    session_sequence INTEGER,
    metadata VARCHAR(4000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_playback_events_event_id UNIQUE (event_id),
    CONSTRAINT fk_playback_events_session FOREIGN KEY (playback_session_id) REFERENCES playback_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_playback_events_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_playback_events_content FOREIGN KEY (content_id) REFERENCES content(id) ON DELETE CASCADE,
    CONSTRAINT fk_playback_events_video_asset FOREIGN KEY (video_asset_id) REFERENCES video_assets(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_playback_events_user_occurred ON playback_events(user_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_playback_events_content_occurred ON playback_events(content_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_playback_events_session_occurred ON playback_events(playback_session_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_playback_events_type_occurred ON playback_events(event_type, occurred_at);
