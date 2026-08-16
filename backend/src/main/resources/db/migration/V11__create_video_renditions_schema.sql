-- V11: Create video renditions schema for multi-bitrate/multi-resolution transcoding ladder

CREATE TABLE IF NOT EXISTS video_renditions (
    id BIGSERIAL PRIMARY KEY,
    video_asset_id BIGINT NOT NULL,
    resolution VARCHAR(20) NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    video_codec VARCHAR(50) NOT NULL DEFAULT 'h264',
    audio_codec VARCHAR(50) NOT NULL DEFAULT 'aac',
    bitrate_kbps INTEGER NOT NULL,
    audio_bitrate_kbps INTEGER NOT NULL DEFAULT 128,
    frame_rate DOUBLE PRECISION,
    file_size_bytes BIGINT NOT NULL,
    storage_bucket VARCHAR(100) NOT NULL,
    storage_key VARCHAR(500) NOT NULL UNIQUE,
    checksum_sha256 VARCHAR(64) NOT NULL,
    duration_seconds INTEGER,
    status VARCHAR(50) NOT NULL DEFAULT 'READY',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_video_renditions_asset FOREIGN KEY (video_asset_id) REFERENCES video_assets(id) ON DELETE CASCADE,
    CONSTRAINT uq_video_rendition_asset_res UNIQUE (video_asset_id, resolution)
);

CREATE INDEX IF NOT EXISTS idx_video_renditions_asset_id ON video_renditions(video_asset_id);
CREATE INDEX IF NOT EXISTS idx_video_renditions_status ON video_renditions(status);
CREATE INDEX IF NOT EXISTS idx_video_renditions_resolution ON video_renditions(resolution);
