-- ====================================================================
-- Phase 6.6: Analytics Aggregation Foundation Schema
-- ====================================================================

CREATE TABLE IF NOT EXISTS analytics_daily_metrics (
    id BIGSERIAL PRIMARY KEY,
    metric_date DATE NOT NULL,
    content_id BIGINT NOT NULL,
    platform VARCHAR(32) NOT NULL DEFAULT 'WEB',
    total_sessions INTEGER NOT NULL DEFAULT 0,
    total_plays INTEGER NOT NULL DEFAULT 0,
    unique_viewers INTEGER NOT NULL DEFAULT 0,
    total_watch_time_seconds BIGINT NOT NULL DEFAULT 0,
    completion_count INTEGER NOT NULL DEFAULT 0,
    pause_count INTEGER NOT NULL DEFAULT 0,
    seek_count INTEGER NOT NULL DEFAULT 0,
    buffer_event_count INTEGER NOT NULL DEFAULT 0,
    error_count INTEGER NOT NULL DEFAULT 0,
    quality_change_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_analytics_daily_metric UNIQUE (metric_date, content_id, platform),
    CONSTRAINT fk_analytics_daily_content FOREIGN KEY (content_id) REFERENCES content(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_analytics_daily_metric_date ON analytics_daily_metrics(metric_date DESC);
CREATE INDEX IF NOT EXISTS idx_analytics_daily_content_date ON analytics_daily_metrics(content_id, metric_date DESC);
CREATE INDEX IF NOT EXISTS idx_analytics_daily_platform_date ON analytics_daily_metrics(platform, metric_date DESC);

CREATE TABLE IF NOT EXISTS analytics_aggregation_checkpoint (
    id BIGSERIAL PRIMARY KEY,
    consumer_name VARCHAR(64) NOT NULL,
    last_processed_event_id BIGINT NOT NULL DEFAULT 0,
    last_processed_occurred_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_analytics_checkpoint_consumer UNIQUE (consumer_name)
);
