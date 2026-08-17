-- ==============================================================================
-- CommunityOTT Monolithic Backend - V15: Create Continue Watching Index
-- ==============================================================================

CREATE INDEX IF NOT EXISTS idx_watch_progress_continue_watching
    ON watch_progress (user_id, completed, position_seconds, last_watched_at DESC);
