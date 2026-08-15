-- ===================================================================
-- CommunityOTT Authentication Schema Creation Migration (Flyway V4__create_authentication_schema.sql)
-- Defines core authentication entities: otp_requests, auth_sessions
-- ===================================================================

-- 1. OTP Requests Table
CREATE TABLE IF NOT EXISTS otp_requests (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NULL,
    identifier_type VARCHAR(32) NOT NULL,
    identifier VARCHAR(255) NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'REQUESTED',
    attempt_count INT NOT NULL DEFAULT 0,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    verified_at TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT fk_otp_requests_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_otp_identifier_type CHECK (identifier_type IN ('EMAIL', 'PHONE')),
    CONSTRAINT chk_otp_purpose CHECK (purpose IN ('LOGIN', 'REGISTRATION', 'ACCOUNT_RECOVERY')),
    CONSTRAINT chk_otp_status CHECK (status IN ('REQUESTED', 'VERIFIED', 'EXPIRED', 'FAILED', 'LOCKED'))
);

-- 2. Auth Sessions Table
CREATE TABLE IF NOT EXISTS auth_sessions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    device_id VARCHAR(255) NOT NULL,
    device_name VARCHAR(255),
    platform VARCHAR(32) NOT NULL,
    refresh_token_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    last_used_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE NULL,
    ip_address VARCHAR(45),
    user_agent VARCHAR(512),
    CONSTRAINT fk_auth_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_auth_session_platform CHECK (platform IN ('IOS', 'ANDROID', 'WEB'))
);

-- ===================================================================
-- INDEXES
-- ===================================================================

CREATE INDEX IF NOT EXISTS idx_otp_requests_user_id ON otp_requests(user_id);
CREATE INDEX IF NOT EXISTS idx_otp_requests_identifier ON otp_requests(identifier);
CREATE INDEX IF NOT EXISTS idx_otp_requests_created_at ON otp_requests(created_at);
CREATE INDEX IF NOT EXISTS idx_otp_requests_expires_at ON otp_requests(expires_at);
CREATE INDEX IF NOT EXISTS idx_otp_requests_status ON otp_requests(status);

CREATE INDEX IF NOT EXISTS idx_auth_sessions_user_id ON auth_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_auth_sessions_refresh_token_hash ON auth_sessions(refresh_token_hash);
CREATE INDEX IF NOT EXISTS idx_auth_sessions_user_device ON auth_sessions(user_id, device_id);
CREATE INDEX IF NOT EXISTS idx_auth_sessions_expires_at ON auth_sessions(expires_at);
CREATE INDEX IF NOT EXISTS idx_auth_sessions_revoked_at ON auth_sessions(revoked_at);
