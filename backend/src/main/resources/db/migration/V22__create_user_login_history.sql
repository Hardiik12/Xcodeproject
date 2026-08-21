-- Flyway Migration: V22__create_user_login_history.sql
-- User-Facing Login History & Account Security Projection Table

CREATE TABLE user_login_history (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id                 BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_type              VARCHAR(64) NOT NULL,
    status                  VARCHAR(32) NOT NULL,
    device_id               BIGINT REFERENCES devices(id) ON DELETE SET NULL,
    device_name             VARCHAR(128) NOT NULL,
    platform                VARCHAR(32) NOT NULL,
    os_version              VARCHAR(64),
    app_version             VARCHAR(64),
    masked_ip               VARCHAR(64) NOT NULL,
    approx_location         VARCHAR(128),
    user_message            VARCHAR(255) NOT NULL,
    occurred_at             TIMESTAMPTZ NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    CONSTRAINT chk_login_history_status CHECK (status IN ('SUCCESS', 'FAILURE', 'BLOCKED'))
);

-- Optimized Composite Indexes for User Account Security Queries
CREATE INDEX idx_login_history_user_occurred ON user_login_history (user_id, occurred_at DESC, id DESC);
CREATE INDEX idx_login_history_user_type_occurred ON user_login_history (user_id, event_type, occurred_at DESC, id DESC);
