-- Flyway Migration: V21__create_security_audit_events.sql
-- Module: Security Event Audit Infrastructure (Phase C.2)

CREATE TABLE security_audit_events (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL,
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    event_type VARCHAR(100) NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    reason_code VARCHAR(100),
    device_id BIGINT REFERENCES devices(id) ON DELETE SET NULL,
    device_identifier VARCHAR(255) NOT NULL,
    session_id BIGINT REFERENCES auth_sessions(id) ON DELETE SET NULL,
    platform VARCHAR(50) NOT NULL,
    app_version VARCHAR(50),
    ip_address VARCHAR(45) NOT NULL,
    user_agent VARCHAR(500),
    request_id VARCHAR(100),
    trace_id VARCHAR(100),
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_security_events_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE', 'BLOCKED'))
);

CREATE INDEX idx_security_events_user_time ON security_audit_events(user_id, created_at DESC);
CREATE INDEX idx_security_events_type_time ON security_audit_events(event_type, created_at DESC);
CREATE INDEX idx_security_events_device_time ON security_audit_events(device_id, created_at DESC);
CREATE INDEX idx_security_events_session_time ON security_audit_events(session_id, created_at DESC);
CREATE INDEX idx_security_events_ip_time ON security_audit_events(ip_address, created_at DESC);
CREATE INDEX idx_security_events_request_id ON security_audit_events(request_id);
