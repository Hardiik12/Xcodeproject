-- Flyway Migration: V23__create_security_alerts.sql
-- User-Facing Security Alerts Table

CREATE TABLE security_alerts (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    alert_type          VARCHAR(64) NOT NULL,
    severity            VARCHAR(32) NOT NULL,
    title               VARCHAR(128) NOT NULL,
    message             VARCHAR(255) NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'UNREAD',
    source_event_id     UUID,
    device_id           BIGINT REFERENCES devices(id) ON DELETE SET NULL,
    platform            VARCHAR(32),
    masked_ip           VARCHAR(64),
    approx_location     VARCHAR(128),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    read_at             TIMESTAMPTZ,
    expires_at          TIMESTAMPTZ NOT NULL,
    
    CONSTRAINT chk_alert_severity CHECK (severity IN ('INFO', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT chk_alert_status CHECK (status IN ('UNREAD', 'READ', 'ARCHIVED'))
);

CREATE INDEX idx_security_alerts_user_status_created ON security_alerts (user_id, status, created_at DESC, id DESC);
CREATE INDEX idx_security_alerts_user_created ON security_alerts (user_id, created_at DESC, id DESC);
CREATE INDEX idx_security_alerts_expires ON security_alerts (expires_at);
CREATE UNIQUE INDEX unq_security_alerts_user_event ON security_alerts (user_id, alert_type, source_event_id) WHERE source_event_id IS NOT NULL;
