-- ===================================================================
-- CommunityOTT Auth Sessions Enhancement Migration (Flyway V19)
-- Adds previous_refresh_token_hash column and index for token reuse detection
-- ===================================================================

ALTER TABLE auth_sessions
ADD COLUMN IF NOT EXISTS previous_refresh_token_hash VARCHAR(255) NULL;

CREATE INDEX IF NOT EXISTS idx_auth_sessions_prev_refresh_hash
ON auth_sessions(previous_refresh_token_hash);
