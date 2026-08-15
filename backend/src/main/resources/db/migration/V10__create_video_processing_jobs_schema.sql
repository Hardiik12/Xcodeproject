-- ===================================================================
-- Phase 5.5: Video Processing Job Architecture Schema
-- ===================================================================

CREATE TABLE IF NOT EXISTS video_processing_jobs (
    id BIGSERIAL PRIMARY KEY,
    video_asset_id BIGINT NOT NULL,
    job_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'QUEUED',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    priority INTEGER NOT NULL DEFAULT 0,
    worker_id VARCHAR(100),
    error_code VARCHAR(100),
    error_message VARCHAR(1000),
    media_metadata_json TEXT,
    created_by BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    failed_at TIMESTAMP WITH TIME ZONE,
    last_heartbeat_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_video_processing_jobs_asset FOREIGN KEY (video_asset_id) REFERENCES video_assets(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_video_processing_jobs_asset_id ON video_processing_jobs(video_asset_id);
CREATE INDEX IF NOT EXISTS idx_video_processing_jobs_status ON video_processing_jobs(status);
CREATE INDEX IF NOT EXISTS idx_video_processing_jobs_job_type ON video_processing_jobs(job_type);
CREATE INDEX IF NOT EXISTS idx_video_processing_jobs_stale ON video_processing_jobs(status, last_heartbeat_at);
