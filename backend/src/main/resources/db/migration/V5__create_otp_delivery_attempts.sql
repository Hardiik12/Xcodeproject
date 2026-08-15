-- ===================================================================
-- CommunityOTT OTP Delivery Attempts Schema Migration (Flyway V5__create_otp_delivery_attempts.sql)
-- Defines audit log table for provider-independent OTP delivery attempts
-- ===================================================================

CREATE TABLE IF NOT EXISTS otp_delivery_attempts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    otp_request_id BIGINT NOT NULL,
    channel VARCHAR(32) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    provider_message_id VARCHAR(255) NULL,
    failure_code VARCHAR(64) NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    delivered_at TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT fk_otp_delivery_attempts_request FOREIGN KEY (otp_request_id) REFERENCES otp_requests(id) ON DELETE CASCADE,
    CONSTRAINT chk_otp_delivery_channel CHECK (channel IN ('EMAIL', 'PHONE')),
    CONSTRAINT chk_otp_delivery_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_otp_delivery_attempts_request_id ON otp_delivery_attempts(otp_request_id);
CREATE INDEX IF NOT EXISTS idx_otp_delivery_attempts_created_at ON otp_delivery_attempts(created_at);
CREATE INDEX IF NOT EXISTS idx_otp_delivery_attempts_status ON otp_delivery_attempts(status);
