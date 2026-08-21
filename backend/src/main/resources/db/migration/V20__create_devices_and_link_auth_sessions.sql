-- Flyway Migration: V20__create_devices_and_link_auth_sessions.sql
-- Description: Create devices table for 2-device registration limit and link to auth_sessions

CREATE TABLE devices (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_identifier VARCHAR(255) NOT NULL,
    platform VARCHAR(32) NOT NULL,
    device_model VARCHAR(255),
    os_version VARCHAR(64),
    app_version VARCHAR(64),
    display_name VARCHAR(255),
    first_registered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_active_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_user_device_identifier UNIQUE (user_id, device_identifier)
);

CREATE INDEX idx_devices_user_active ON devices(user_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_devices_identifier ON devices(device_identifier);

-- Add Foreign Key to auth_sessions
ALTER TABLE auth_sessions ADD COLUMN device_entity_id BIGINT REFERENCES devices(id) ON DELETE CASCADE;
CREATE INDEX idx_auth_sessions_device_entity ON auth_sessions(device_entity_id);
