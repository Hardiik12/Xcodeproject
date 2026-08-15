-- Phase 5.3: Add optimistic locking version column and lifecycle indexes to content table

ALTER TABLE content ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_content_status ON content(status);
CREATE INDEX IF NOT EXISTS idx_content_created_by ON content(created_by);
