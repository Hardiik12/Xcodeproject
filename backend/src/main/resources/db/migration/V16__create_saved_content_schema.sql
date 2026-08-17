-- ==============================================================================
-- CommunityOTT Monolithic Backend - V16: Create Saved Content (My List) Schema
-- ==============================================================================

CREATE TABLE IF NOT EXISTS saved_content (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    content_id BIGINT NOT NULL,
    saved_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_saved_content_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_saved_content_content FOREIGN KEY (content_id) REFERENCES content(id) ON DELETE CASCADE,
    CONSTRAINT uq_saved_content_user_content UNIQUE (user_id, content_id)
);

CREATE INDEX IF NOT EXISTS idx_saved_content_user_saved_at ON saved_content(user_id, saved_at DESC);
CREATE INDEX IF NOT EXISTS idx_saved_content_content_id ON saved_content(content_id);
