-- V12: Create HLS packaging and adaptive streaming manifest schema

CREATE TABLE IF NOT EXISTS video_hls_packages (
    id BIGSERIAL PRIMARY KEY,
    video_asset_id BIGINT NOT NULL,
    processing_job_id BIGINT,
    master_playlist_key VARCHAR(500) NOT NULL UNIQUE,
    storage_bucket VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'READY',
    variant_count INTEGER NOT NULL DEFAULT 0,
    target_duration_seconds INTEGER NOT NULL DEFAULT 2,
    error_code VARCHAR(100),
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_hls_packages_asset FOREIGN KEY (video_asset_id) REFERENCES video_assets(id) ON DELETE CASCADE,
    CONSTRAINT fk_hls_packages_job FOREIGN KEY (processing_job_id) REFERENCES video_processing_jobs(id) ON DELETE SET NULL,
    CONSTRAINT uq_hls_package_asset UNIQUE (video_asset_id)
);

CREATE INDEX IF NOT EXISTS idx_hls_packages_asset_id ON video_hls_packages(video_asset_id);
CREATE INDEX IF NOT EXISTS idx_hls_packages_status ON video_hls_packages(status);

CREATE TABLE IF NOT EXISTS video_hls_variants (
    id BIGSERIAL PRIMARY KEY,
    hls_package_id BIGINT NOT NULL,
    video_rendition_id BIGINT,
    resolution VARCHAR(20) NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    playlist_key VARCHAR(500) NOT NULL UNIQUE,
    init_segment_key VARCHAR(500) NOT NULL,
    segment_count INTEGER NOT NULL DEFAULT 0,
    target_duration_seconds INTEGER NOT NULL DEFAULT 2,
    bandwidth_bps BIGINT NOT NULL,
    average_bandwidth_bps BIGINT,
    codecs VARCHAR(100) NOT NULL DEFAULT 'avc1.4d401f,mp4a.40.2',
    frame_rate DOUBLE PRECISION,
    status VARCHAR(50) NOT NULL DEFAULT 'READY',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_hls_variants_package FOREIGN KEY (hls_package_id) REFERENCES video_hls_packages(id) ON DELETE CASCADE,
    CONSTRAINT fk_hls_variants_rendition FOREIGN KEY (video_rendition_id) REFERENCES video_renditions(id) ON DELETE SET NULL,
    CONSTRAINT uq_hls_variant_package_res UNIQUE (hls_package_id, resolution)
);

CREATE INDEX IF NOT EXISTS idx_hls_variants_package_id ON video_hls_variants(hls_package_id);
CREATE INDEX IF NOT EXISTS idx_hls_variants_rendition_id ON video_hls_variants(video_rendition_id);
CREATE INDEX IF NOT EXISTS idx_hls_variants_status ON video_hls_variants(status);
