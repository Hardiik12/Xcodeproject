-- V13: Create playback sessions and watch progress schema

CREATE TABLE IF NOT EXISTS playback_sessions (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    content_id BIGINT NOT NULL,
    video_asset_id BIGINT NOT NULL,
    device_id VARCHAR(255),
    platform VARCHAR(32) NOT NULL DEFAULT 'WEB',
    status VARCHAR(32) NOT NULL DEFAULT 'STARTED',
    last_position_seconds INTEGER NOT NULL DEFAULT 0,
    duration_seconds INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_heartbeat_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_playback_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_playback_sessions_content FOREIGN KEY (content_id) REFERENCES content(id) ON DELETE CASCADE,
    CONSTRAINT fk_playback_sessions_asset FOREIGN KEY (video_asset_id) REFERENCES video_assets(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_playback_sessions_user_id ON playback_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_playback_sessions_content_id ON playback_sessions(content_id);
CREATE INDEX IF NOT EXISTS idx_playback_sessions_status ON playback_sessions(status);
CREATE INDEX IF NOT EXISTS idx_playback_sessions_last_heartbeat ON playback_sessions(last_heartbeat_at);
CREATE INDEX IF NOT EXISTS idx_playback_sessions_session_id ON playback_sessions(session_id);

CREATE TABLE IF NOT EXISTS watch_progress (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    content_id BIGINT NOT NULL,
    video_asset_id BIGINT NOT NULL,
    position_seconds INTEGER NOT NULL DEFAULT 0,
    duration_seconds INTEGER NOT NULL DEFAULT 0,
    completion_percentage DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    last_watched_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_watch_progress_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_watch_progress_content FOREIGN KEY (content_id) REFERENCES content(id) ON DELETE CASCADE,
    CONSTRAINT fk_watch_progress_asset FOREIGN KEY (video_asset_id) REFERENCES video_assets(id) ON DELETE CASCADE,
    CONSTRAINT uq_watch_progress_user_content UNIQUE (user_id, content_id)
);

CREATE INDEX IF NOT EXISTS idx_watch_progress_user_id ON watch_progress(user_id);
CREATE INDEX IF NOT EXISTS idx_watch_progress_user_last_watched ON watch_progress(user_id, last_watched_at DESC);
CREATE INDEX IF NOT EXISTS idx_watch_progress_content_id ON watch_progress(content_id);
